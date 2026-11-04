package br.edu.puccampinas.fastattendance.sessao.servidor;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.protocolo.Envelope;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Erro;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import br.edu.puccampinas.fastattendance.sessao.protocolo.Json;
import org.java_websocket.WebSocket;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

/**
 * Estado de uma conexão WebSocket.
 * <p>
 * Cada conexão tem uma caixa de entrada consumida por uma <b>virtual thread</b> própria: as
 * mensagens de um cliente são processadas em ordem, e o processamento pode bloquear (ex.: acesso ao
 * MongoDB) sem ocupar as threads de I/O do Java-WebSocket nem as das outras conexões.
 */
public final class Conexao {

    /** Mensagens pendentes por conexão; acima disso o cliente está inundando o servidor. */
    static final int CAPACIDADE_CAIXA = 64;

    private static final String FIM = new String("<fim>");

    private final WebSocket socket;
    private final Json json;
    private final BlockingQueue<String> caixa = new ArrayBlockingQueue<>(CAPACIDADE_CAIXA);
    private final List<Future<?>> tarefas = new CopyOnWriteArrayList<>();
    private final List<Runnable> acoesAoFechar = new CopyOnWriteArrayList<>();
    private final LimitadorDeMensagens limitador = new LimitadorDeMensagens(Clock.systemUTC());

    private volatile UsuarioAutenticado usuario;
    private volatile long ultimaAtividadeNanos = System.nanoTime();

    Conexao(WebSocket socket, Json json, ProcessadorDeMensagens processador) {
        this.socket = socket;
        this.json = json;
        Thread.ofVirtual()
                .name("conexao-" + socket.getRemoteSocketAddress())
                .start(() -> consumir(processador));
    }

    private void consumir(ProcessadorDeMensagens processador) {
        try {
            while (true) {
                String texto = caixa.take();
                if (texto == FIM) {
                    return;
                }
                try {
                    processador.processar(this, texto);
                } catch (RuntimeException e) {
                    System.getLogger(Conexao.class.getName())
                            .log(System.Logger.Level.ERROR, "Falha ao processar mensagem", e);
                    enviar(Envelope.evento(TipoMensagem.ERRO, new Erro(CodigoErro.ERRO_INTERNO, "Erro inesperado")));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Chamado pela thread de I/O do Java-WebSocket; nunca bloqueia. */
    void receber(String texto) {
        ultimaAtividadeNanos = System.nanoTime();
        if (!caixa.offer(texto)) {
            fechar(CodigoFechamento.VIOLACAO_POLITICA, "Mensagens demais sem resposta");
        }
    }

    void encerrar() {
        tarefas.forEach(t -> t.cancel(false));
        acoesAoFechar.forEach(acao -> {
            try {
                acao.run();
            } catch (RuntimeException e) {
                System.getLogger(Conexao.class.getName())
                        .log(System.Logger.Level.WARNING, "Falha em ação de fechamento da conexão", e);
            }
        });
        caixa.clear();
        caixa.offer(FIM);
    }

    /** Registra uma ação executada quando a conexão fecha (ex.: remover o professor dos acompanhantes). */
    public void aoFechar(Runnable acao) {
        acoesAoFechar.add(acao);
    }

    public void enviar(Envelope<?> envelope) {
        if (socket.isOpen()) {
            socket.send(json.escrever(envelope));
        }
    }

    public void fechar(int codigo, String motivo) {
        socket.close(codigo, motivo);
    }

    /** Registra uma tarefa agendada para ser cancelada quando a conexão fechar. */
    public void vincular(Future<?> tarefa) {
        tarefas.add(tarefa);
    }

    public LimitadorDeMensagens limitador() {
        return limitador;
    }

    public boolean autenticada() {
        return usuario != null;
    }

    public UsuarioAutenticado usuario() {
        return usuario;
    }

    public void autenticar(UsuarioAutenticado usuario) {
        this.usuario = usuario;
    }

    boolean inativaHaMaisDe(Duration limite) {
        return System.nanoTime() - ultimaAtividadeNanos > limite.toNanos();
    }
}
