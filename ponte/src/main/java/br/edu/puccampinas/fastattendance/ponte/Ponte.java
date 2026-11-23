/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.Security;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * Ponte WebSocket↔TCP entre os apps web e o servidor de sessão no modelo cliente-servidor por sockets.
 * <p>
 * Os navegadores falam o protocolo v1 (JSON em frames de texto, docs/protocolo-v1.md). Para cada
 * conexão WebSocket, a ponte abre um socket TCP ao servidor de sessão e converte: frames em
 * comunicados ({@link ConversorDeFrames}) e comunicados em frames ({@link ConversorParaNavegador}).
 * Veja {@code docs/servidor-modelo-socket.md}, seção 5, e o "Ponte" dessa mesma documentação.
 * <p>
 * Uso: {@code java -jar ponte.jar [PORTA]}. Variáveis: {@code PONTE_PORTA}, {@code SESSAO_HOST},
 * {@code SESSAO_PORTA}, {@code SESSAO_PRAZO_AUTH_MS}, {@code SESSAO_TIMEOUT_INATIVIDADE_MS}.
 */
public final class Ponte extends WebSocketServer {

    private static final System.Logger LOG = System.getLogger(Ponte.class.getName());

    /** Mensagens do protocolo são pequenas; frames maiores são recusados pelo WebSocket. */
    static final int TAMANHO_MAXIMO_FRAME = 16 * 1024;

    /** Detecta conexões TCP mortas via ping/pong do próprio WebSocket. */
    static final int TIMEOUT_CONEXAO_PERDIDA_SEGUNDOS = 30;

    private final ConfiguracaoPonte config;
    private final ScheduledExecutorService agendador;
    private final Json json = new Json();
    private final CompletableFuture<Integer> iniciado = new CompletableFuture<>();

    public Ponte(ConfiguracaoPonte config) {
        super(new InetSocketAddress(config.porta()), List.of(new Draft_6455(List.of(), TAMANHO_MAXIMO_FRAME)));
        this.config = config;
        this.agendador = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("ponte-agendador").factory());
        // Só com porta fixa (reinício rápido). Com a porta 0 dos testes, o SO_REUSEADDR deixa o macOS
        // escolher uma porta já ocupada em 127.0.0.1 por outro processo
        setReuseAddr(config.porta() != 0);
        setTcpNoDelay(true);
        setConnectionLostTimeout(TIMEOUT_CONEXAO_PERDIDA_SEGUNDOS);
    }

    @Override
    public void onStart() {
        LOG.log(INFO, "Ponte ouvindo na porta {0}; servidor de sessão em {1}:{2}", String.valueOf(getPort()),
                config.sessaoHost(), String.valueOf(config.sessaoPorta()));
        iniciado.complete(getPort());
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake handshake) {
        ConexaoDoNavegador conexao = new ConexaoDoNavegador(socket, config, agendador, json);
        socket.setAttachment(conexao);
        conexao.iniciar();
        LOG.log(DEBUG, "Conexão aberta: {0}", socket.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket socket, String texto) {
        ConexaoDoNavegador conexao = socket.getAttachment();
        if (conexao != null) {
            conexao.receberTexto(texto);
        }
    }

    @Override
    public void onMessage(WebSocket socket, ByteBuffer bytes) {
        ConexaoDoNavegador conexao = socket.getAttachment();
        if (conexao != null) {
            conexao.receberBinario();
        }
    }

    @Override
    public void onClose(WebSocket socket, int codigo, String motivo, boolean remoto) {
        ConexaoDoNavegador conexao = socket.getAttachment();
        if (conexao != null) {
            conexao.navegadorFechou();
        }
        LOG.log(DEBUG, "Conexão fechada ({0}): {1}", codigo, motivo);
    }

    @Override
    public void onError(WebSocket socket, Exception erro) {
        if (socket == null) {
            // Falha do próprio servidor da ponte (ex.: porta em uso)
            iniciado.completeExceptionally(erro);
        }
        LOG.log(ERROR, "Erro na ponte", erro);
    }

    /** Aguarda a ponte começar a aceitar conexões e devolve a porta em uso. */
    public int aguardarInicio(long timeout, TimeUnit unidade) throws Exception {
        return iniciado.get(timeout, unidade);
    }

    /**
     * Fecha todos os navegadores com 1001 (para que reconectem), para o servidor e libera o agendador.
     * É o desligamento gracioso da ponte, chamado pelo shutdown hook no SIGTERM.
     */
    public void encerrar(int timeoutMs) throws InterruptedException {
        for (WebSocket socket : getConnections()) {
            ConexaoDoNavegador conexao = socket.getAttachment();
            if (conexao != null) {
                conexao.fechar(CodigoFechamento.SERVIDOR_REINICIANDO, "Servidor reiniciando");
            }
        }
        try {
            stop(timeoutMs);
        } finally {
            agendador.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Uso esperado: java -jar ponte.jar [PORTA]\n");
            return;
        }
        // Os endereços dos servidores de sessão são resolvidos a cada conexão; a JVM não deve reter por muito tempo
        Security.setProperty("networkaddress.cache.ttl", "5");

        ConfiguracaoPonte config;
        try {
            config = ConfiguracaoPonte.doAmbiente(System.getenv(), args.length == 1 ? args[0] : null);
        } catch (IllegalStateException erro) {
            System.err.println("Configuração inválida: " + erro.getMessage() + "\n");
            return;
        }

        Ponte ponte = new Ponte(config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ponte.encerrar(1_000);
            } catch (InterruptedException erro) {
                Thread.currentThread().interrupt();
            }
        }, "ponte-desligamento"));
        ponte.start();
        // O WebSocketServer roda em threads próprias; a principal espera o sinal de desligamento
        new CountDownLatch(1).await();
    }
}
