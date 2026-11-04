package br.edu.puccampinas.fastattendance.sessao.servidor;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.protocolo.Envelope;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Erro;
import br.edu.puccampinas.fastattendance.sessao.Configuracao;
import br.edu.puccampinas.fastattendance.sessao.protocolo.Json;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * Servidor WebSocket do caminho crítico de check-in, sem framework.
 * <p>
 * As threads de I/O do Java-WebSocket apenas recebem os frames e os entregam à {@link Conexao},
 * que os processa em uma virtual thread própria.
 */
public final class ServidorSessao extends WebSocketServer {

    private static final System.Logger LOG = System.getLogger(ServidorSessao.class.getName());

    /** Mensagens do protocolo são pequenas; frames maiores são recusados. */
    static final int TAMANHO_MAXIMO_FRAME = 16 * 1024;

    /** Detecta conexões TCP mortas via ping/pong do próprio WebSocket. */
    private static final int TIMEOUT_CONEXAO_PERDIDA_SEGUNDOS = 30;

    private final Configuracao configuracao;
    private final ProcessadorDeMensagens processador;
    private final ScheduledExecutorService agendador;
    private final Json json;
    private final CompletableFuture<Integer> iniciado = new CompletableFuture<>();
    private final List<AutoCloseable> recursos = new CopyOnWriteArrayList<>();

    public ServidorSessao(Configuracao configuracao, ProcessadorDeMensagens processador,
                          ScheduledExecutorService agendador, Json json) {
        super(new InetSocketAddress(configuracao.porta()),
                List.of(new Draft_6455(List.of(), TAMANHO_MAXIMO_FRAME)));
        this.configuracao = configuracao;
        this.processador = processador;
        this.agendador = agendador;
        this.json = json;
        // Só com porta fixa (reinício rápido sem esperar o TIME_WAIT). Com a porta 0 dos testes, o
        // SO_REUSEADDR deixa o macOS escolher uma porta já ocupada em 127.0.0.1 por outro processo
        setReuseAddr(configuracao.porta() != 0);
        setTcpNoDelay(true);
        setConnectionLostTimeout(TIMEOUT_CONEXAO_PERDIDA_SEGUNDOS);
    }

    @Override
    public void onStart() {
        long intervalo = Math.max(1, configuracao.timeoutInatividade().toMillis() / 4);
        agendador.scheduleAtFixedRate(this::fecharInativas, intervalo, intervalo, TimeUnit.MILLISECONDS);
        LOG.log(INFO, "Servidor de sessão ouvindo na porta {0}", String.valueOf(getPort()));
        iniciado.complete(getPort());
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake handshake) {
        Conexao conexao = new Conexao(socket, json, processador);
        socket.setAttachment(conexao);
        conexao.vincular(agendador.schedule(() -> {
            if (!conexao.autenticada()) {
                conexao.fechar(CodigoFechamento.AUTENTICACAO_FALHOU, "Autenticação não recebida no prazo");
            }
        }, configuracao.prazoAutenticacao().toMillis(), TimeUnit.MILLISECONDS));
        LOG.log(DEBUG, "Conexão aberta: {0}", socket.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket socket, String texto) {
        Conexao conexao = socket.getAttachment();
        if (conexao != null) {
            conexao.receber(texto);
        }
    }

    @Override
    public void onMessage(WebSocket socket, ByteBuffer bytes) {
        Conexao conexao = socket.getAttachment();
        if (conexao != null) {
            conexao.enviar(Envelope.evento(TipoMensagem.ERRO,
                    new Erro(CodigoErro.MENSAGEM_INVALIDA, "O protocolo aceita apenas frames de texto")));
        }
    }

    @Override
    public void onClose(WebSocket socket, int codigo, String motivo, boolean remoto) {
        Conexao conexao = socket.getAttachment();
        if (conexao != null) {
            conexao.encerrar();
        }
        LOG.log(DEBUG, "Conexão fechada ({0}): {1}", codigo, motivo);
    }

    @Override
    public void onError(WebSocket socket, Exception erro) {
        if (socket == null) {
            // Falha do próprio servidor (ex.: porta em uso)
            iniciado.completeExceptionally(erro);
        }
        LOG.log(ERROR, "Erro no servidor de sessão", erro);
    }

    /**
     * Fecha todas as conexões com 1001 (going away), para que os clientes reconectem em outra
     * réplica, e libera o agendador.
     */
    public void encerrar(int timeoutMs) throws InterruptedException {
        try {
            stop(timeoutMs, "Servidor reiniciando");
        } finally {
            agendador.shutdownNow();
            for (AutoCloseable recurso : recursos) {
                try {
                    recurso.close();
                } catch (Exception e) {
                    LOG.log(ERROR, "Falha ao liberar recurso no encerramento", e);
                }
            }
        }
    }

    /** Recurso liberado em {@link #encerrar(int)}, depois das conexões (ex.: o cliente do MongoDB). */
    public void aoEncerrar(AutoCloseable recurso) {
        recursos.add(recurso);
    }

    /** Aguarda o servidor começar a aceitar conexões e devolve a porta em uso. */
    public int aguardarInicio(long timeout, TimeUnit unidade) throws Exception {
        return iniciado.get(timeout, unidade);
    }

    private void fecharInativas() {
        for (WebSocket socket : getConnections()) {
            Conexao conexao = socket.getAttachment();
            if (conexao != null && conexao.inativaHaMaisDe(configuracao.timeoutInatividade())) {
                conexao.fechar(CodigoFechamento.NORMAL, "Inatividade");
            }
        }
    }
}
