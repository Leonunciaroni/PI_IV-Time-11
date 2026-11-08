package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.ComunicadoDeDesligamento;
import br.edu.puccampinas.fastattendance.common.comunicado.Erro;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDePresencaAtualizada;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDeSessaoEncerrada;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoParaSair;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import org.java_websocket.WebSocket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

/**
 * Uma conexão do navegador ligada a um socket TCP no servidor de sessão.
 * <p>
 * Threads: a do WebSocket só enfileira o frame (nunca bloqueia); uma thread virtual consome a caixa de
 * entrada em ordem e encaminha os pedidos ao servidor; outra lê os comunicados do servidor e os
 * converte em frames. Os fechamentos passam por {@link #fechar(int, String)}, que só age uma vez.
 */
final class ConexaoDoNavegador {

    private static final System.Logger LOG = System.getLogger(ConexaoDoNavegador.class.getName());

    /** Frames de texto pendentes de processamento por conexão; acima disso o cliente inunda a ponte. */
    static final int CAPACIDADE_CAIXA = 64;

    /** Prazo para abrir o socket TCP ao servidor de sessão. */
    static final long PRAZO_CONEXAO_MS = 5_000;

    private static final String FIM = new String("<fim>");

    private final WebSocket navegador;
    private final ConfiguracaoPonte config;
    private final ScheduledExecutorService agendador;
    private final ConversorDeFrames conversorDeFrames;
    private final ConversorParaNavegador conversorParaNavegador;
    private final LimitadorDeMensagens limitador = new LimitadorDeMensagens(Clock.systemUTC());
    private final PendentesDeResposta pendentes = new PendentesDeResposta();
    private final BlockingQueue<String> caixa = new ArrayBlockingQueue<>(CAPACIDADE_CAIXA);
    /** Trava a escrita no servidor: pedido e entrada na fila de pendentes saem juntos, em ordem. */
    private final Object envio = new Object();
    private final AtomicBoolean fechamentoIniciado = new AtomicBoolean(false);

    private volatile Parceiro servidor;
    private volatile boolean autenticacaoEnviada;
    private volatile boolean autenticada;
    private volatile Long expiraEmMs;
    private volatile long ultimoFrameMs = System.currentTimeMillis();
    private volatile ScheduledFuture<?> prazoAutenticacao;
    /** Só é usado pela thread leitora. */
    private int rejeicoesDoServidor;

    ConexaoDoNavegador(WebSocket navegador, ConfiguracaoPonte config, ScheduledExecutorService agendador, Json json) {
        this.navegador = navegador;
        this.config = config;
        this.agendador = agendador;
        this.conversorDeFrames = new ConversorDeFrames(json);
        this.conversorParaNavegador = new ConversorParaNavegador(json);
    }

    /** Inicia a conexão com o servidor e o prazo de autenticação. Chamado ao abrir o WebSocket. */
    void iniciar() {
        Thread.ofVirtual().name("ponte-navegador").start(this::consumir);
        prazoAutenticacao = agendador.schedule(() -> {
            if (!autenticada) {
                fechar(CodigoFechamento.AUTENTICACAO_FALHOU, "Autenticação não recebida no prazo");
            }
        }, config.prazoAutenticacao().toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Frame de texto recebido (thread do WebSocket). */
    void receberTexto(String texto) {
        ultimoFrameMs = System.currentTimeMillis();
        if (!caixa.offer(texto)) {
            fechar(CodigoFechamento.VIOLACAO_POLITICA, "Mensagens demais sem resposta");
        }
    }

    /** Frame binário: o protocolo aceita só texto. Não conta como atividade do servidor, porque não é encaminhado. */
    void receberBinario() {
        enviar(conversorParaNavegador.paraFrame(
                new Erro(CodigoErro.MENSAGEM_INVALIDA, "O protocolo aceita apenas frames de texto"), null));
    }

    /** O navegador fechou o WebSocket (ou a própria ponte fechou e o WebSocket confirmou). */
    void navegadorFechou() {
        if (fechamentoIniciado.compareAndSet(false, true)) {
            avisarSaida();
        }
        liberarRecursos();
    }

    /** Fecha o WebSocket com o código e o motivo indicados e libera o servidor. Só a primeira chamada age. */
    void fechar(int codigo, String motivo) {
        if (!fechamentoIniciado.compareAndSet(false, true)) {
            return;
        }
        LOG.log(DEBUG, "Fechando conexão do navegador com {0}: {1}", codigo, motivo);
        try {
            navegador.close(codigo, motivo);
        } catch (RuntimeException falha) {
            LOG.log(DEBUG, "Falha ao fechar o WebSocket", falha);
        }
        liberarRecursos();
    }

    // ---- Conexão com o servidor de sessão ----

    private void consumir() {
        try {
            servidor = conectar();
        } catch (IOException falha) {
            LOG.log(ERROR, "Servidor de sessão indisponível: {0}", falha.toString());
            fechar(CodigoFechamento.SERVIDOR_REINICIANDO, "Servidor de sessão indisponível");
            return;
        }
        if (fechamentoIniciado.get()) {
            // Fechou enquanto conectava (ex.: prazo de autenticação): o socket recém-aberto não pode ficar
            servidor.adeus();
            return;
        }
        Thread.ofVirtual().name("ponte-servidor").start(this::lerDoServidor);

        try {
            while (true) {
                String texto = caixa.take();
                if (texto == FIM || fechamentoIniciado.get()) {
                    return;
                }
                try {
                    processar(texto);
                } catch (RuntimeException falha) {
                    LOG.log(ERROR, "Falha ao processar frame", falha);
                    enviar(conversorParaNavegador.paraFrame(
                            new Erro(CodigoErro.ERRO_INTERNO, "Erro inesperado"), null));
                }
            }
        } catch (InterruptedException interrompida) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Abre o socket TCP e faz o cabeçalho do fluxo, como o cliente do modelo: primeiro o
     * {@code ObjectOutputStream}, depois o {@code ObjectInputStream}. Com várias réplicas do servidor
     * atrás do nome {@code SESSAO_HOST}, sorteia a ordem dos endereços e tenta os seguintes se um falhar.
     */
    private Parceiro conectar() throws IOException {
        List<InetAddress> enderecos = new ArrayList<>(List.of(InetAddress.getAllByName(config.sessaoHost())));
        Collections.shuffle(enderecos);
        IOException ultima = null;
        for (InetAddress endereco : enderecos) {
            Socket socket = new Socket();
            try {
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(endereco, config.sessaoPorta()), (int) PRAZO_CONEXAO_MS);
                socket.setSoTimeout((int) PRAZO_CONEXAO_MS);
                ObjectOutputStream transmissor = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream receptor = new ObjectInputStream(socket.getInputStream());
                receptor.setObjectInputFilter(FiltroDeComunicados.FILTRO);
                socket.setSoTimeout(0);
                return new Parceiro(socket, receptor, transmissor);
            } catch (IOException falha) {
                ultima = falha;
                try {
                    socket.close();
                } catch (IOException fechada) {
                    // já fechado
                }
            }
        }
        throw ultima != null ? ultima : new UnknownHostException(config.sessaoHost());
    }

    /** Processa um frame do navegador (thread consumidora, em ordem). */
    private void processar(String texto) {
        if (!limitador.registrar()) {
            enviar(conversorParaNavegador.paraFrame(
                    new Erro(CodigoErro.LIMITE_EXCEDIDO, "Mensagens demais em pouco tempo"),
                    conversorDeFrames.idSeLegivel(texto)));
            if (limitador.excessoRepetido()) {
                fechar(CodigoFechamento.VIOLACAO_POLITICA, "Limite de mensagens excedido repetidamente");
            }
            return;
        }

        ConversorDeFrames.Recebido recebido;
        try {
            recebido = conversorDeFrames.analisar(texto, autenticacaoEnviada);
        } catch (FalhaDeProtocolo falha) {
            enviar(conversorParaNavegador.paraFrame(new Erro(falha.codigo(), falha.getMessage()), falha.ref()));
            if (falha.fechamento() != 0) {
                fechar(falha.fechamento(), falha.getMessage());
            }
            return;
        }

        if (recebido.tipo() == TipoMensagem.AUTH) {
            autenticacaoEnviada = true;
        }
        encaminhar(recebido.comunicado(), recebido.id());
    }

    /**
     * Envia um pedido ao servidor e o põe na fila de pendentes, sob a mesma trava: a ordem da fila é a
     * ordem em que o servidor vai responder.
     */
    private void encaminhar(Comunicado pedido, String id) {
        synchronized (envio) {
            if (!pendentes.enfileirar(id)) {
                fechar(CodigoFechamento.VIOLACAO_POLITICA, "Mensagens demais sem resposta");
                return;
            }
            try {
                servidor.receba(pedido);
            } catch (IOException falha) {
                LOG.log(DEBUG, "Falha ao enviar ao servidor de sessão", falha);
                tratarFimDoServidor();
            }
        }
    }

    /** Thread leitora: converte cada comunicado do servidor em frame, ou fecha, conforme o caso. */
    private void lerDoServidor() {
        try {
            while (!fechamentoIniciado.get()) {
                Comunicado comunicado = servidor.envie();
                if (comunicado instanceof ComunicadoDeDesligamento) {
                    fechar(CodigoFechamento.SERVIDOR_REINICIANDO, "Servidor reiniciando");
                    return;
                }
                if (comunicado instanceof EventoDePresencaAtualizada || comunicado instanceof EventoDeSessaoEncerrada) {
                    enviar(conversorParaNavegador.paraFrame(comunicado, null));
                } else {
                    tratarResposta(comunicado);
                }
            }
        } catch (Exception falha) {
            // EOF, queda ou comunicado que a desserialização recusou: o servidor não está mais respondendo
            LOG.log(DEBUG, "Conexão com o servidor de sessão terminou: {0}", falha.toString());
            tratarFimDoServidor();
        }
    }

    /** Atribui a resposta à cabeça da fila de pendentes e a converte em frame com o {@code ref} dela. */
    private void tratarResposta(Comunicado resposta) {
        PendentesDeResposta.Pendencia pendencia = pendentes.desenfileirar();
        if (pendencia == null) {
            // O servidor deveria responder só a pedidos; se respondeu sem pedido, o erro é interno
            LOG.log(ERROR, "Resposta sem pedido pendente: {0}", resposta);
            enviar(conversorParaNavegador.paraFrame(new Erro(CodigoErro.ERRO_INTERNO, "Erro inesperado"), null));
            return;
        }
        String ref = pendencia.ref();

        if (resposta instanceof RespostaDeAutenticacao autenticacao) {
            expiraEmMs = autenticacao.getExpiraEm();
            autenticada = true;
            ScheduledFuture<?> prazo = prazoAutenticacao;
            if (prazo != null) {
                prazo.cancel(false);
            }
        }

        if (resposta instanceof Erro erro) {
            rejeicoesDoServidor = erro.getCodigo() == CodigoErro.LIMITE_EXCEDIDO ? rejeicoesDoServidor + 1 : 0;
            enviar(conversorParaNavegador.paraFrame(erro, ref));
            if (erro.getCodigo() == CodigoErro.AUTH_INVALIDA) {
                fechar(CodigoFechamento.AUTENTICACAO_FALHOU, "Autenticação inválida");
            } else if (rejeicoesDoServidor >= LimitadorDeMensagens.REJEICOES_ATE_FECHAR) {
                fechar(CodigoFechamento.VIOLACAO_POLITICA, "Limite de mensagens excedido repetidamente");
            }
            return;
        }

        rejeicoesDoServidor = 0;
        enviar(conversorParaNavegador.paraFrame(resposta, ref));
    }

    /**
     * O socket do servidor terminou sem comunicado de explicação. Decide o código pela heurística de
     * {@link DecisorDeFechamento}. Se a conexão já estava fechando, não faz nada.
     */
    private void tratarFimDoServidor() {
        if (fechamentoIniciado.get()) {
            return;
        }
        DecisorDeFechamento.Fechamento fechamento = DecisorDeFechamento.aposFimDoServidor(
                autenticada, expiraEmMs, System.currentTimeMillis(), ultimoFrameMs,
                config.timeoutInatividade().toMillis());
        fechar(fechamento.codigo(), fechamento.motivo());
    }

    // ---- Envio e liberação ----

    private void enviar(String frame) {
        if (!fechamentoIniciado.get() && navegador.isOpen()) {
            navegador.send(frame);
        }
    }

    /** Avisa o servidor de que a conexão vai sair. Só se for o navegador que fechou. */
    private void avisarSaida() {
        Parceiro s = servidor;
        if (s == null) {
            return;
        }
        try {
            synchronized (envio) {
                s.receba(new PedidoParaSair());
            }
        } catch (IOException | RuntimeException falha) {
            // o servidor já pode ter fechado a conexão; o adeus abaixo cuida do socket
        }
    }

    /** Cancela o prazo, encerra a thread consumidora e fecha o socket do servidor. Idempotente. */
    private void liberarRecursos() {
        ScheduledFuture<?> prazo = prazoAutenticacao;
        if (prazo != null) {
            prazo.cancel(false);
        }
        caixa.clear();
        caixa.offer(FIM);
        Parceiro s = servidor;
        if (s != null) {
            s.adeus();
        }
    }
}
