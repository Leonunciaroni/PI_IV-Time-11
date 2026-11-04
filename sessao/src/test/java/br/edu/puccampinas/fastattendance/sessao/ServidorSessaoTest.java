package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.servidor.ServidorSessao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ServidorSessaoTest {

    private static final String SEGREDO = "segredo-dos-testes-com-mais-de-32-bytes-0123456789";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private ServidorSessao servidor;
    private int porta;

    @BeforeEach
    void iniciar() throws Exception {
        // Prazo folgado: conectar uma sala cheia leva centenas de milissegundos em máquinas lentas
        iniciar(Duration.ofSeconds(10));
    }

    private void iniciar(Duration prazoAutenticacao) throws Exception {
        servidor = SessaoApplication.iniciar(
                new Configuracao(0, SEGREDO, MongoDeTeste.uri(), prazoAutenticacao, Duration.ofSeconds(60),
                        Duration.ofHours(4)));
        porta = servidor.getPort();
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        servidor.encerrar(1_000);
    }

    @Test
    void respondePingAntesDaAutenticacaoComRefDaRequisicao() throws Exception {
        ClienteTeste cliente = conectar();

        cliente.enviar(mensagem("ping", "p1", "{}"));

        JsonNode resposta = ler(cliente.proxima());
        assertEquals(1, resposta.get("v").intValue());
        assertEquals("pong", resposta.get("tipo").stringValue());
        assertEquals("p1", resposta.get("ref").stringValue());
        assertTrue(resposta.get("ts").longValue() > 0);
    }

    @Test
    void autenticaComJwtValidoEmitidoPelaApi() throws Exception {
        ClienteTeste cliente = conectar();
        String token = jwt.emitir("66f1b9ff", "Prof. Matheus", Papel.PROFESSOR).token();

        cliente.enviar(mensagem("auth", "a1", "{\"token\":\"" + token + "\"}"));

        JsonNode resposta = ler(cliente.proxima());
        assertEquals("auth.ok", resposta.get("tipo").stringValue());
        assertEquals("a1", resposta.get("ref").stringValue());
        assertEquals("66f1b9ff", resposta.get("dados").get("usuarioId").stringValue());
        assertEquals("PROFESSOR", resposta.get("dados").get("papel").stringValue());
    }

    @Test
    void tokenInvalidoRecebeErroEFechamento4001() throws Exception {
        ClienteTeste cliente = conectar();
        String deOutroSegredo = new JwtService("outro-segredo-tambem-com-mais-de-32-bytes!!", Duration.ofHours(1))
                .emitir("1", "Intruso", Papel.PROFESSOR).token();

        cliente.enviar(mensagem("auth", "a1", "{\"token\":\"" + deOutroSegredo + "\"}"));

        assertErro(cliente.proxima(), "AUTH_INVALIDA");
        assertEquals(CodigoFechamento.AUTENTICACAO_FALHOU, cliente.codigoFechamento(2_000));
    }

    @Test
    void fechaCom4001QuandoAuthNaoChegaNoPrazo() throws Exception {
        // Prazo curto só aqui, para o teste não demorar
        servidor.encerrar(1_000);
        iniciar(Duration.ofMillis(500));
        ClienteTeste cliente = conectar();

        assertEquals(CodigoFechamento.AUTENTICACAO_FALHOU, cliente.codigoFechamento(2_000));
    }

    @Test
    void exigeAutenticacaoParaOutrasMensagens() throws Exception {
        ClienteTeste cliente = conectar();

        cliente.enviar(mensagem("checkin", "c1", "{\"sessaoId\":\"x\",\"token\":\"123456\"}"));

        JsonNode resposta = assertErro(cliente.proxima(), "AUTH_REQUERIDA");
        assertEquals("c1", resposta.get("ref").stringValue());
    }

    @Test
    void versaoDiferenteRecebeErroEFechamento4002() throws Exception {
        ClienteTeste cliente = conectar();

        cliente.enviar("{\"v\":2,\"tipo\":\"ping\",\"id\":\"p1\",\"ts\":1,\"dados\":{}}");

        assertErro(cliente.proxima(), "VERSAO_NAO_SUPORTADA");
        assertEquals(CodigoFechamento.VERSAO_NAO_SUPORTADA, cliente.codigoFechamento(2_000));
    }

    @Test
    void mensagensInvalidasRecebemErroSemDerrubarAConexao() throws Exception {
        ClienteTeste cliente = conectar();

        cliente.enviar("isto não é json");
        assertErro(cliente.proxima(), "MENSAGEM_INVALIDA");

        cliente.enviar(mensagem("tipo.inexistente", "x1", "{}"));
        assertErro(cliente.proxima(), "MENSAGEM_INVALIDA");

        // Tipos servidor → cliente não podem ser enviados pelo cliente
        cliente.enviar(mensagem("auth.ok", "x2", "{}"));
        assertErro(cliente.proxima(), "MENSAGEM_INVALIDA");

        cliente.enviar(mensagem("ping", "p1", "{}"));
        assertEquals("pong", ler(cliente.proxima()).get("tipo").stringValue());
        assertFalse(cliente.fechada());
    }

    @Test
    void fechaCom4003QuandoOTokenExpiraDuranteAConexao() throws Exception {
        ClienteTeste cliente = conectar();
        String token = new JwtService(SEGREDO, Duration.ofSeconds(1)).emitir("1", "Ana", Papel.ALUNO).token();

        cliente.enviar(mensagem("auth", "a1", "{\"token\":\"" + token + "\"}"));
        assertEquals("auth.ok", ler(cliente.proxima()).get("tipo").stringValue());

        assertEquals(CodigoFechamento.TOKEN_EXPIROU, cliente.codigoFechamento(3_000));
    }

    @Test
    void processaMensagensDeUmaConexaoNaOrdemDeChegada() throws Exception {
        ClienteTeste cliente = conectar();

        for (int i = 0; i < 50; i++) {
            cliente.enviar(mensagem("ping", "p" + i, "{}"));
        }

        for (int i = 0; i < 50; i++) {
            assertEquals("p" + i, ler(cliente.proxima()).get("ref").stringValue());
        }
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
            sala.get(i).enviar(mensagem("auth", "a" + i, "{\"token\":\"" + token + "\"}"));
        }

        for (int i = 0; i < alunos; i++) {
            JsonNode resposta = ler(sala.get(i).proxima());
            assertEquals("auth.ok", resposta.get("tipo").stringValue());
            assertEquals("aluno-" + i, resposta.get("dados").get("usuarioId").stringValue());
        }
    }

    private ClienteTeste conectar() {
        ClienteTeste cliente = new ClienteTeste(porta);
        clientes.add(cliente);
        return cliente;
    }

    private static String mensagem(String tipo, String id, String dados) {
        return "{\"v\":1,\"tipo\":\"%s\",\"id\":\"%s\",\"ts\":%d,\"dados\":%s}"
                .formatted(tipo, id, System.currentTimeMillis(), dados);
    }

    private static JsonNode ler(String texto) {
        return JSON.readTree(texto);
    }

    private static JsonNode assertErro(String texto, String codigoEsperado) {
        JsonNode resposta = ler(texto);
        assertEquals("erro", resposta.get("tipo").stringValue(), texto);
        assertEquals(codigoEsperado, resposta.get("dados").get("codigo").stringValue(), texto);
        return resposta;
    }
}
