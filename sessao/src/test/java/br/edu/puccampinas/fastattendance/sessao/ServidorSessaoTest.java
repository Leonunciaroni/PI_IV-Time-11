/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.ComunicadoDeDesligamento;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDePing;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDePong;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.servidor.LimitadorDeMensagens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transporte e regras da supervisora: autenticação, prazos, limite de mensagens, inatividade,
 * desligamento e a allowlist de desserialização. Cada teste usa a conexão de um {@link ClienteTeste}.
 */
class ServidorSessaoTest {

    private static final String SEGREDO = ServidorDeTeste.SEGREDO;

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private ServidorDeTeste servidor;
    private int porta;

    @BeforeEach
    void iniciar() throws Exception {
        // Prazo folgado: conectar uma sala cheia leva centenas de milissegundos em máquinas lentas
        servidor = new ServidorDeTeste(Duration.ofSeconds(10), Duration.ofSeconds(60));
        porta = servidor.porta();
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        servidor.close();
    }

    @Test
    void respondePingAntesDaAutenticacao() throws Exception {
        ClienteTeste cliente = conectar();

        cliente.enviar(new PedidoDePing());

        cliente.proximo(RespostaDePong.class);
    }

    @Test
    void autenticaComJwtValidoEmitidoPelaApi() throws Exception {
        ClienteTeste cliente = conectar();
        String token = jwt.emitir("66f1b9ff", "Prof. Matheus", Papel.PROFESSOR).token();

        cliente.enviar(new PedidoDeAutenticacao(token));

        RespostaDeAutenticacao resposta = cliente.proximo(RespostaDeAutenticacao.class);
        assertEquals("66f1b9ff", resposta.getUsuarioId());
        assertEquals(Papel.PROFESSOR, resposta.getPapel());
        assertTrue(resposta.getExpiraEm() > System.currentTimeMillis());
    }

    @Test
    void tokenInvalidoRecebeErroEFechaAConexao() throws Exception {
        ClienteTeste cliente = conectar();
        String deOutroSegredo = new JwtService("outro-segredo-tambem-com-mais-de-32-bytes!!", Duration.ofHours(1))
                .emitir("1", "Intruso", Papel.PROFESSOR).token();

        cliente.enviar(new PedidoDeAutenticacao(deOutroSegredo));

        cliente.proximoErro(CodigoErro.AUTH_INVALIDA);
        cliente.aguardarFechamento(2_000);
    }

    @Test
    void fechaAConexaoQuandoAuthNaoChegaNoPrazo() throws Exception {
        // Prazo curto só aqui, para o teste não demorar
        servidor.close();
        servidor = new ServidorDeTeste(Duration.ofMillis(500), Duration.ofSeconds(60));
        porta = servidor.porta();
        ClienteTeste cliente = conectar();

        cliente.aguardarFechamento(3_000);
    }

    @Test
    void exigeAutenticacaoParaOutrasMensagens() throws Exception {
        ClienteTeste cliente = conectar();

        cliente.enviar(new PedidoDeCheckin("66f1c2a1b2c3d4e5f6a7b8c9", "123456", OrigemPresenca.QRCODE,
                "disp", -22.8, -47.0, 5.0));

        cliente.proximoErro(CodigoErro.AUTH_REQUERIDA);
    }

    @Test
    void comunicadoQueOClienteNaoEnviaRecebeMensagemInvalida() throws Exception {
        ClienteTeste cliente = conectar();

        // Respostas e desligamento são só do servidor
        cliente.enviar(new RespostaDePong());
        cliente.proximoErro(CodigoErro.MENSAGEM_INVALIDA);

        cliente.enviar(new ComunicadoDeDesligamento());
        cliente.proximoErro(CodigoErro.MENSAGEM_INVALIDA);

        cliente.enviar(new PedidoDePing());
        cliente.proximo(RespostaDePong.class);
        assertNull(cliente.aguardar(200));
    }

    @Test
    void fechaAConexaoQuandoOTokenExpira() throws Exception {
        ClienteTeste cliente = conectar();
        String token = new JwtService(SEGREDO, Duration.ofSeconds(1)).emitir("1", "Ana", Papel.ALUNO).token();

        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);

        cliente.aguardarFechamento(3_000);
    }

    @Test
    void umaRespostaPorPedidoNaOrdemDeChegada() throws Exception {
        ClienteTeste cliente = conectar();
        String token = jwt.emitir("66f1b9ff", "Prof. Matheus", Papel.PROFESSOR).token();
        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);

        for (int i = 0; i < 25; i++) {
            cliente.enviar(new PedidoDePing());
            cliente.enviar(new PedidoDeAcompanharSessao(null));
        }

        for (int i = 0; i < 25; i++) {
            cliente.proximo(RespostaDePong.class);
            cliente.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
        }
        assertNull(cliente.aguardar(200));
    }

    @Test
    void recusaMensagensAcimaDoLimitePorConexaoEFechaQuandoInsiste() throws Exception {
        ClienteTeste cliente = conectar();
        for (int i = 0; i < LimitadorDeMensagens.MAXIMO; i++) {
            cliente.enviar(new PedidoDePing());
        }
        for (int i = 0; i < LimitadorDeMensagens.MAXIMO; i++) {
            cliente.proximo(RespostaDePong.class);
        }

        cliente.enviar(new PedidoDePing());
        cliente.proximoErro(CodigoErro.LIMITE_EXCEDIDO);

        // Recusas seguidas até o limite de insistência: a última fecha a conexão
        for (int i = 1; i < LimitadorDeMensagens.REJEICOES_ATE_FECHAR; i++) {
            cliente.enviar(new PedidoDePing());
            cliente.proximoErro(CodigoErro.LIMITE_EXCEDIDO);
        }
        cliente.aguardarFechamento(2_000);
    }

    @Test
    void atendeUmaSalaCheiaAutenticandoAoMesmoTempo() throws Exception {
        int alunos = 70;
        List<ClienteTeste> sala = new ArrayList<>();
        for (int i = 0; i < alunos; i++) {
            sala.add(conectar());
        }

        for (int i = 0; i < alunos; i++) {
            String token = jwt.emitir("aluno-" + i, "Aluno " + i, Papel.ALUNO).token();
            sala.get(i).enviar(new PedidoDeAutenticacao(token));
        }

        for (int i = 0; i < alunos; i++) {
            RespostaDeAutenticacao resposta = sala.get(i).proximo(RespostaDeAutenticacao.class);
            assertEquals("aluno-" + i, resposta.getUsuarioId());
        }
    }

    @Test
    void fechaAConexaoSemTraficoPelaInatividade() throws Exception {
        servidor.close();
        servidor = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofMillis(300));
        porta = servidor.porta();
        ClienteTeste cliente = conectar();
        String token = jwt.emitir("aluno-1", "Ana", Papel.ALUNO).token();
        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);

        cliente.aguardarFechamento(2_000);
    }

    @Test
    void desativarEnviaComunicadoDeDesligamentoEFechaAsConexoes() throws Exception {
        ClienteTeste cliente = conectar();
        String token = jwt.emitir("aluno-1", "Ana", Papel.ALUNO).token();
        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);

        servidor.desativar();

        cliente.proximo(ComunicadoDeDesligamento.class);
        cliente.aguardarFechamento(2_000);
    }

    @Test
    void recusaClasseForaDaAllowlistEFechaSemAfetarOutrasConexoes() throws Exception {
        ClienteTeste intruso = conectar();

        // Comunicado de classe que não é do pacote common.comunicado: a desserialização a recusa
        intruso.enviar(new ComunicadoEstranho());

        intruso.aguardarFechamento(2_000);
        ClienteTeste outro = conectar();
        outro.enviar(new PedidoDePing());
        outro.proximo(RespostaDePong.class);
    }

    /** Comunicado fora do pacote do protocolo: a allowlist de {@code FiltroDeComunicados} não o aceita. */
    static final class ComunicadoEstranho extends Comunicado {
        private static final long serialVersionUID = 1L;
    }

    private ClienteTeste conectar() throws Exception {
        ClienteTeste cliente = new ClienteTeste(porta);
        clientes.add(cliente);
        return cliente;
    }
}
