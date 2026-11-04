package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.servidor.ServidorSessao;
import br.edu.puccampinas.fastattendance.sessao.totp.SegredoTotp;
import br.edu.puccampinas.fastattendance.sessao.totp.ValidadorTotp;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/** {@code checkin} pelo canal QR Code, com o MongoDB real e as regras antifraude (docs/protocolo-v1.md, 4.8 e 7). */
class CheckinTest {

    private static final String SEGREDO = "segredo-dos-testes-com-mais-de-32-bytes-0123456789";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Sala de referência: coordenadas do exemplo da seção 9 do protocolo, raio de 50 m. */
    private static final double SALA_LAT = -22.8339;
    private static final double SALA_LON = -47.0525;
    private static final double RAIO_SALA = 50;

    /** 0,0001 grau de latitude (cerca de 11 m) e 0,001 grau (cerca de 111 m). */
    private static final double PERTO_LAT = SALA_LAT + 0.0001;
    private static final double LONGE_LAT = SALA_LAT + 0.001;

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final MongoCollection<Document> presencas = MongoDeTeste.banco().getCollection("presencas");
    private ServidorSessao servidor;

    private String professorId;
    private String turmaId;
    private String salaId;
    private String ana;
    private String bruno;
    private String alunoDeOutraTurma;

    @BeforeEach
    void preparar() throws Exception {
        MongoDeTeste.limpar();
        professorId = new ObjectId().toHexString();
        turmaId = inserir("turmas", new Document("nome", "Turma A").append("professorId", professorId));
        salaId = inserir("salas", new Document("nome", "H15 - Sala 203")
                .append("latitude", SALA_LAT).append("longitude", SALA_LON).append("raioMetros", RAIO_SALA));
        ana = aluno("Ana Souza");
        bruno = aluno("Bruno Lima");
        alunoDeOutraTurma = aluno("Diego Alves");
        for (String alunoId : List.of(ana, bruno)) {
            inserir("matriculas", new Document("turmaId", turmaId).append("alunoId", alunoId));
        }
        servidor = SessaoApplication.iniciar(new Configuracao(0, SEGREDO, MongoDeTeste.uri(),
                Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofHours(4)));
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        servidor.encerrar(1_000);
    }

    // ---- Caminho feliz e distância ----

    @Test
    void registraPresencaDeAlunoMatriculadoDentroDoRaio() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-ana", PERTO_LAT, SALA_LON));

        JsonNode resposta = ler(aluno.proxima());
        assertEquals("checkin.ok", resposta.get("tipo").stringValue(), resposta.toString());
        assertEquals("c1", resposta.get("ref").stringValue());
        assertEquals(sessao.id(), resposta.get("dados").get("sessaoId").stringValue());

        Document gravada = presencas.find(eq("alunoId", ana)).first();
        assertNotNull(gravada);
        assertEquals(sessao.id(), gravada.getString("sessaoId"));
        assertEquals(turmaId, gravada.getString("turmaId"));
        assertTrue(gravada.getBoolean("presente"));
        assertEquals("QRCODE", gravada.getString("origem"));
        assertEquals("disp-ana", gravada.getString("dispositivoId"));
        assertEquals(PERTO_LAT, gravada.getDouble("latitude"));
        assertEquals(resposta.get("dados").get("registradoEm").longValue(), gravada.getDate("registradoEm").getTime());
    }

    @Test
    void aceitaTokenDaJanelaAnterior() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        String tokenAnterior = ValidadorTotp.gerar(sessao.segredo(), instanteAgora() - SegredoTotp.PERIODO_SEGUNDOS);

        aluno.enviar(checkin("c1", sessao, tokenAnterior, "disp-ana", SALA_LAT, SALA_LON));

        assertEquals("checkin.ok", ler(aluno.proxima()).get("tipo").stringValue());
    }

    @Test
    void foraDoRaioRecusaComDistanciaNaMensagem() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-ana", LONGE_LAT, SALA_LON));

        JsonNode resposta = assertErro(aluno.proxima(), "FORA_DO_RAIO");
        assertEquals("c1", resposta.get("ref").stringValue());
        assertTrue(resposta.get("dados").get("mensagem").stringValue().contains("111"));
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Token ----

    @Test
    void tokenDeOutraSessaoRecusaComTokenInvalido() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        // Segredo Base32 do exemplo da seção 5.3 do protocolo, diferente do segredo desta sessão
        String token = ValidadorTotp.gerar("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", instanteAgora());

        aluno.enviar(checkin("c1", sessao, token, "disp-ana", SALA_LAT, SALA_LON));

        assertErro(aluno.proxima(), "TOKEN_INVALIDO");
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void tokenMalformadoRecusaComTokenInvalido() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin("c1", sessao, "12ab56", "disp-ana", SALA_LAT, SALA_LON));

        assertErro(aluno.proxima(), "TOKEN_INVALIDO");
    }

    @Test
    void tokenDeJanelaJaExpiradaRecusaComTokenExpirado() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        // Duas janelas atrás: fora das aceitas (atual e anterior), mas ainda reconhecida como expirada
        String token = ValidadorTotp.gerar(sessao.segredo(), instanteAgora() - 2L * SegredoTotp.PERIODO_SEGUNDOS);

        aluno.enviar(checkin("c1", sessao, token, "disp-ana", SALA_LAT, SALA_LON));

        assertErro(aluno.proxima(), "TOKEN_EXPIRADO");
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Matrícula ----

    @Test
    void recusaAlunoNaoMatriculadoNaTurma() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(alunoDeOutraTurma, Papel.ALUNO);

        aluno.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-diego", SALA_LAT, SALA_LON));

        assertErro(aluno.proxima(), "ALUNO_NAO_MATRICULADO");
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Duplicidade e dispositivo ----

    @Test
    void recusaSegundoCheckinDoMesmoAlunoComPresencaDuplicada() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        aluno.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));
        assertEquals("checkin.ok", ler(aluno.proxima()).get("tipo").stringValue());

        aluno.enviar(checkin("c2", sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));
        assertErro(aluno.proxima(), "PRESENCA_DUPLICADA");

        // Mesmo com outro dispositivo, o aluno não registra duas vezes
        aluno.enviar(checkin("c3", sessao, tokenAtual(sessao), "disp-outro", SALA_LAT, SALA_LON));
        assertErro(aluno.proxima(), "PRESENCA_DUPLICADA");
        assertEquals(1, presencas.countDocuments(eq("sessaoId", sessao.id())));
    }

    @Test
    void mesmoDispositivoComOutroAlunoRecusaComDispositivoJaUtilizado() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste ana1 = autenticado(ana, Papel.ALUNO);
        ana1.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-compartilhado", SALA_LAT, SALA_LON));
        assertEquals("checkin.ok", ler(ana1.proxima()).get("tipo").stringValue());

        ClienteTeste bruno1 = autenticado(bruno, Papel.ALUNO);
        bruno1.enviar(checkin("c2", sessao, tokenAtual(sessao), "disp-compartilhado", SALA_LAT, SALA_LON));

        assertErro(bruno1.proxima(), "DISPOSITIVO_JA_UTILIZADO");
        assertEquals(0, presencas.countDocuments(eq("alunoId", bruno)));
    }

    @Test
    void dispositivoPodeSerReutilizadoEmOutraSessao() throws Exception {
        Sessao primeira = abrirQrcode();
        ClienteTeste ana1 = autenticado(ana, Papel.ALUNO);
        ana1.enviar(checkin("c1", primeira, tokenAtual(primeira), "disp-x", SALA_LAT, SALA_LON));
        ana1.proxima();
        encerrar(primeira);

        Sessao segunda = abrirQrcode();
        ClienteTeste bruno1 = autenticado(bruno, Papel.ALUNO);
        bruno1.enviar(checkin("c2", segunda, tokenAtual(segunda), "disp-x", SALA_LAT, SALA_LON));

        assertEquals("checkin.ok", ler(bruno1.proxima()).get("tipo").stringValue());
    }

    // ---- Estado da sessão ----

    @Test
    void recusaSessaoEncerradaOuInexistente() throws Exception {
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        aluno.enviar(checkin("c0", new Sessao(new ObjectId().toHexString(), null), "123456", "disp-ana",
                SALA_LAT, SALA_LON));
        assertErro(aluno.proxima(), "SESSAO_NAO_ENCONTRADA");

        Sessao sessao = abrirQrcode();
        encerrar(sessao);
        aluno.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));
        assertErro(aluno.proxima(), "SESSAO_ENCERRADA");
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void recusaSessaoEmModoClassico() throws Exception {
        String sessaoId = abrirSessao("CLASSICA");
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin("c1", new Sessao(sessaoId, null), "123456", "disp-ana", SALA_LAT, SALA_LON));

        assertErro(aluno.proxima(), "MODO_INCOMPATIVEL");
    }

    @Test
    void somenteAlunoFazCheckin() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste professor = autenticado(professorId, Papel.PROFESSOR);

        professor.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-prof", SALA_LAT, SALA_LON));

        assertErro(professor.proxima(), "PERMISSAO_NEGADA");
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Campos ----

    @Test
    void exigeCamposDoCanalQrcode() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(mensagem("checkin", "c1", "{\"sessaoId\":\"%s\",\"token\":\"%s\",\"canal\":\"QRCODE\","
                + "\"dispositivoId\":\"disp-ana\",\"longitude\":%s,\"precisaoMetros\":5}"
                .formatted(sessao.id(), tokenAtual(sessao), SALA_LON)));
        assertErro(aluno.proxima(), "MENSAGEM_INVALIDA");

        aluno.enviar(mensagem("checkin", "c2", "{\"sessaoId\":\"%s\",\"token\":\"%s\",\"canal\":\"QRCODE\","
                + "\"latitude\":%s,\"longitude\":%s,\"precisaoMetros\":5}"
                .formatted(sessao.id(), tokenAtual(sessao), SALA_LAT, SALA_LON)));
        assertErro(aluno.proxima(), "MENSAGEM_INVALIDA");

        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void canalSomAindaNaoEAceito() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(mensagem("checkin", "c1", "{\"sessaoId\":\"%s\",\"token\":\"%s\",\"canal\":\"SOM\"}"
                .formatted(sessao.id(), tokenAtual(sessao))));

        assertErro(aluno.proxima(), "MENSAGEM_INVALIDA");
    }

    @Test
    void coordenadaForaDaFaixaRecusaComMensagemInvalida() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin("c1", sessao, tokenAtual(sessao), "disp-ana", 123.0, SALA_LON));

        assertErro(aluno.proxima(), "MENSAGEM_INVALIDA");
    }

    // ---- Apoio ----

    /** Sessão aberta pelo professor; o segredo TOTP vem de sessao.aberta (nulo quando só o id importa). */
    private record Sessao(String id, String segredo) {
    }

    private Sessao abrirQrcode() throws Exception {
        ClienteTeste professor = autenticado(professorId, Papel.PROFESSOR);
        professor.enviar(mensagem("sessao.abrir", "s1",
                "{\"turmaId\":\"%s\",\"modo\":\"QRCODE\",\"salaId\":\"%s\"}".formatted(turmaId, salaId)));
        JsonNode resposta = ler(professor.proxima());
        assertEquals("sessao.aberta", resposta.get("tipo").stringValue(), resposta.toString());
        return new Sessao(resposta.get("dados").get("sessaoId").stringValue(),
                resposta.get("dados").get("totp").get("segredo").stringValue());
    }

    private String abrirSessao(String modo) throws Exception {
        ClienteTeste professor = autenticado(professorId, Papel.PROFESSOR);
        professor.enviar(mensagem("sessao.abrir", "s1",
                "{\"turmaId\":\"%s\",\"modo\":\"%s\"}".formatted(turmaId, modo)));
        JsonNode resposta = ler(professor.proxima());
        assertEquals("sessao.aberta", resposta.get("tipo").stringValue(), resposta.toString());
        return resposta.get("dados").get("sessaoId").stringValue();
    }

    private void encerrar(Sessao sessao) throws Exception {
        ClienteTeste professor = autenticado(professorId, Papel.PROFESSOR);
        professor.enviar(mensagem("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        assertEquals("sessao.encerrada", ler(professor.proxima()).get("tipo").stringValue());
    }

    private static String tokenAtual(Sessao sessao) {
        return ValidadorTotp.gerar(sessao.segredo(), instanteAgora());
    }

    private static long instanteAgora() {
        return System.currentTimeMillis() / 1000;
    }

    private ClienteTeste autenticado(String usuarioId, Papel papel) throws Exception {
        ClienteTeste cliente = new ClienteTeste(servidor.getPort());
        clientes.add(cliente);
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(mensagem("auth", "a", "{\"token\":\"" + token + "\"}"));
        assertEquals("auth.ok", ler(cliente.proxima()).get("tipo").stringValue());
        return cliente;
    }

    private String aluno(String nome) {
        return inserir("usuarios", new Document("nome", nome).append("papel", "ALUNO"));
    }

    private static String inserir(String colecao, Document documento) {
        ObjectId id = new ObjectId();
        MongoDeTeste.banco().getCollection(colecao).insertOne(documento.append("_id", id));
        return id.toHexString();
    }

    private static String checkin(String id, Sessao sessao, String token, String dispositivo,
                                  double latitude, double longitude) {
        return mensagem("checkin", id, ("{\"sessaoId\":\"%s\",\"token\":\"%s\",\"canal\":\"QRCODE\","
                + "\"dispositivoId\":\"%s\",\"latitude\":%s,\"longitude\":%s,\"precisaoMetros\":10}")
                .formatted(sessao.id(), token, dispositivo, latitude, longitude));
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
