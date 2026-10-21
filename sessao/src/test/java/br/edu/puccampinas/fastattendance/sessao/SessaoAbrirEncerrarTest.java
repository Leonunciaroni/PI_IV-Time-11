package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.servidor.ServidorSessao;
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
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/** {@code sessao.abrir} e {@code sessao.encerrar} pelo WebSocket, com o MongoDB real. */
class SessaoAbrirEncerrarTest {

    private static final String SEGREDO = "segredo-dos-testes-com-mais-de-32-bytes-0123456789";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final MongoCollection<Document> sessoes = MongoDeTeste.banco().getCollection("sessoes");
    private ServidorSessao servidor;

    private String professorId;
    private String turmaId;
    private String turmaDeOutroProfessor;
    private String salaId;

    @BeforeEach
    void preparar() throws Exception {
        MongoDeTeste.limpar();
        professorId = new ObjectId().toHexString();
        turmaId = inserir("turmas", new Document("nome", "Turma A").append("professorId", professorId));
        turmaDeOutroProfessor = inserir("turmas",
                new Document("nome", "Turma B").append("professorId", new ObjectId().toHexString()));
        salaId = inserir("salas", new Document("nome", "H15 - Sala 203").append("latitude", -22.83)
                .append("longitude", -47.05).append("raioMetros", 30.0));
        iniciarServidor(Duration.ofHours(4));
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        servidor.encerrar(1_000);
    }

    // ---- Abertura ----

    @Test
    void abreSessaoEEnviaAConfiguracaoTotpAoProfessor() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(mensagem("sessao.abrir", "s1", "{\"turmaId\":\"%s\",\"modo\":\"CLASSICA\"}".formatted(turmaId)));

        JsonNode resposta = ler(professor.proxima());
        assertEquals("sessao.aberta", resposta.get("tipo").stringValue(), resposta.toString());
        assertEquals("s1", resposta.get("ref").stringValue());
        JsonNode dados = resposta.get("dados");
        assertEquals(turmaId, dados.get("turmaId").stringValue());
        assertEquals("CLASSICA", dados.get("modo").stringValue());
        assertTrue(dados.get("abertaEm").longValue() > 0);
        JsonNode totp = dados.get("totp");
        assertTrue(totp.get("segredo").stringValue().matches("[A-Z2-7]{32}"));
        assertEquals("HmacSHA256", totp.get("algoritmo").stringValue());
        assertEquals(6, totp.get("digitos").intValue());
        assertEquals(15, totp.get("periodoSegundos").intValue());
        assertEquals(0, totp.get("t0").longValue());

        Document gravada = sessoes.find(eq("_id", new ObjectId(dados.get("sessaoId").stringValue()))).first();
        assertNotNull(gravada);
        assertEquals("ABERTA", gravada.getString("status"));
        assertEquals(professorId, gravada.getString("professorId"));
        assertEquals(totp.get("segredo").stringValue(), gravada.getString("segredoTotp"));
    }

    @Test
    void abreSessaoQrCodeComSala() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(abrir(turmaId, "QRCODE", salaId));

        assertEquals("sessao.aberta", ler(professor.proxima()).get("tipo").stringValue());
        assertEquals(salaId, sessoes.find().first().getString("salaId"));
    }

    @Test
    void modoQrCodeExigeSalaExistente() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(abrir(turmaId, "QRCODE", null));
        assertErro(professor.proxima(), "MENSAGEM_INVALIDA");

        professor.enviar(abrir(turmaId, "QRCODE", new ObjectId().toHexString()));
        assertErro(professor.proxima(), "MENSAGEM_INVALIDA");

        assertEquals(0, sessoes.countDocuments());
    }

    @Test
    void recusaPedidoSemTurmaOuComModoDesconhecido() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(mensagem("sessao.abrir", "s1", "{\"modo\":\"CLASSICA\"}"));
        assertErro(professor.proxima(), "MENSAGEM_INVALIDA");

        professor.enviar(abrir(turmaId, "TELEPATIA", null));
        assertErro(professor.proxima(), "MENSAGEM_INVALIDA");
    }

    @Test
    void soODonoDaTurmaAbreSessao() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(abrir(turmaDeOutroProfessor, "CLASSICA", null));
        assertErro(professor.proxima(), "PERMISSAO_NEGADA");

        professor.enviar(abrir(new ObjectId().toHexString(), "CLASSICA", null));
        assertErro(professor.proxima(), "PERMISSAO_NEGADA");

        professor.enviar(abrir("nao-e-um-objectid", "CLASSICA", null));
        assertErro(professor.proxima(), "PERMISSAO_NEGADA");

        assertEquals(0, sessoes.countDocuments());
    }

    @Test
    void alunoNaoAbreSessao() throws Exception {
        ClienteTeste aluno = autenticado(new ObjectId().toHexString(), Papel.ALUNO);

        aluno.enviar(abrir(turmaId, "CLASSICA", null));

        assertErro(aluno.proxima(), "PERMISSAO_NEGADA");
    }

    @Test
    void recusaSegundaSessaoAbertaNaMesmaTurma() throws Exception {
        ClienteTeste professor = professor();
        abrirSessao(professor);

        professor.enviar(abrir(turmaId, "SOM", null));

        assertErro(professor.proxima(), "SESSAO_JA_ABERTA");
        assertEquals(1, sessoes.countDocuments());
    }

    // ---- Encerramento ----

    @Test
    void encerraSessaoContandoSoOsPresentes() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor);
        MongoCollection<Document> presencas = MongoDeTeste.banco().getCollection("presencas");
        presencas.insertMany(List.of(
                new Document("sessaoId", sessaoId).append("alunoId", "a1").append("presente", true),
                new Document("sessaoId", sessaoId).append("alunoId", "a2").append("presente", true),
                new Document("sessaoId", sessaoId).append("alunoId", "a3").append("presente", false)));

        professor.enviar(encerrar(sessaoId));

        JsonNode resposta = ler(professor.proxima());
        assertEquals("sessao.encerrada", resposta.get("tipo").stringValue(), resposta.toString());
        assertEquals("e1", resposta.get("ref").stringValue());
        assertEquals(sessaoId, resposta.get("dados").get("sessaoId").stringValue());
        assertEquals("PROFESSOR", resposta.get("dados").get("motivo").stringValue());
        assertEquals(2, resposta.get("dados").get("totalPresentes").intValue());

        Document gravada = sessoes.find(eq("_id", new ObjectId(sessaoId))).first();
        assertEquals("ENCERRADA", gravada.getString("status"));
        assertEquals("PROFESSOR", gravada.getString("motivoEncerramento"));
        assertEquals(resposta.get("dados").get("encerradaEm").longValue(), gravada.getDate("encerradaEm").getTime());
    }

    @Test
    void permiteNovaSessaoDepoisDoEncerramento() throws Exception {
        ClienteTeste professor = professor();
        String primeira = abrirSessao(professor);
        professor.enviar(encerrar(primeira));
        assertEquals("sessao.encerrada", ler(professor.proxima()).get("tipo").stringValue());

        String segunda = abrirSessao(professor);

        assertNotEquals(primeira, segunda);
        assertNotEquals(sessoes.find(eq("_id", new ObjectId(primeira))).first().getString("segredoTotp"),
                sessoes.find(eq("_id", new ObjectId(segunda))).first().getString("segredoTotp"));
    }

    @Test
    void naoEncerraDuasVezesNemSessaoInexistente() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor);
        professor.enviar(encerrar(sessaoId));
        professor.proxima();

        professor.enviar(encerrar(sessaoId));
        assertErro(professor.proxima(), "SESSAO_ENCERRADA");

        professor.enviar(encerrar(new ObjectId().toHexString()));
        assertErro(professor.proxima(), "SESSAO_NAO_ENCONTRADA");
    }

    @Test
    void soODonoEncerraASessao() throws Exception {
        String sessaoId = abrirSessao(professor());
        ClienteTeste outro = autenticado(new ObjectId().toHexString(), Papel.PROFESSOR);

        outro.enviar(encerrar(sessaoId));

        assertErro(outro.proxima(), "PERMISSAO_NEGADA");
        assertEquals("ABERTA", sessoes.find().first().getString("status"));
    }

    @Test
    void nenhumRegistroEAceitoAposOEncerramento() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor);
        professor.enviar(encerrar(sessaoId));
        professor.proxima();
        ClienteTeste aluno = autenticado(new ObjectId().toHexString(), Papel.ALUNO);

        aluno.enviar(mensagem("checkin", "c1", """
                {"sessaoId":"%s","token":"123456","canal":"QRCODE","dispositivoId":"d1"}""".formatted(sessaoId)));
        assertErro(aluno.proxima(), "SESSAO_ENCERRADA");

        professor.enviar(mensagem("presenca.marcar", "m1", """
                {"sessaoId":"%s","alunoId":"a1","presente":true}""".formatted(sessaoId)));
        assertErro(professor.proxima(), "SESSAO_ENCERRADA");
    }

    // ---- Tempo limite ----

    @Test
    void encerraPorTempoLimiteSemAcaoDoProfessor() throws Exception {
        servidor.encerrar(1_000);
        clientes.clear();
        iniciarServidor(Duration.ofMillis(500));
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor);

        Document gravada = aguardarStatus(sessaoId, "ENCERRADA", 5_000);

        assertEquals("TEMPO_LIMITE", gravada.getString("motivoEncerramento"));
        professor.enviar(encerrar(sessaoId));
        assertErro(professor.proxima(), "SESSAO_ENCERRADA");
    }

    @Test
    void sessaoVencidaNaoImpedeAberturaDeOutra() throws Exception {
        // Sessão esquecida aberta há 5 horas, antes de a varredura passar por ela
        sessoes.insertOne(new Document("_id", new ObjectId()).append("turmaId", turmaId)
                .append("professorId", professorId).append("modo", "CLASSICA").append("status", "ABERTA")
                .append("abertaEm", new Date(System.currentTimeMillis() - Duration.ofHours(5).toMillis())));
        ClienteTeste professor = professor();

        abrirSessao(professor);

        assertEquals(1, sessoes.countDocuments(eq("status", "ABERTA")));
        assertEquals(1, sessoes.countDocuments(eq("motivoEncerramento", "TEMPO_LIMITE")));
    }

    // ---- Apoio ----

    private void iniciarServidor(Duration duracaoMaxima) throws Exception {
        servidor = SessaoApplication.iniciar(new Configuracao(0, SEGREDO, MongoDeTeste.uri(),
                Duration.ofSeconds(5), Duration.ofSeconds(60), duracaoMaxima));
    }

    private ClienteTeste professor() throws Exception {
        return autenticado(professorId, Papel.PROFESSOR);
    }

    private ClienteTeste autenticado(String usuarioId, Papel papel) throws Exception {
        ClienteTeste cliente = new ClienteTeste(servidor.getPort());
        clientes.add(cliente);
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(mensagem("auth", "a", "{\"token\":\"" + token + "\"}"));
        assertEquals("auth.ok", ler(cliente.proxima()).get("tipo").stringValue());
        return cliente;
    }

    private String abrirSessao(ClienteTeste professor) throws Exception {
        professor.enviar(abrir(turmaId, "CLASSICA", null));
        JsonNode resposta = ler(professor.proxima());
        assertEquals("sessao.aberta", resposta.get("tipo").stringValue(), resposta.toString());
        return resposta.get("dados").get("sessaoId").stringValue();
    }

    private Document aguardarStatus(String sessaoId, String status, long timeoutMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            Document sessao = sessoes.find(eq("_id", new ObjectId(sessaoId))).first();
            if (status.equals(sessao.getString("status"))) {
                return sessao;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Sessão não chegou ao status " + status + " em " + timeoutMs + " ms");
    }

    private static String inserir(String colecao, Document documento) {
        ObjectId id = new ObjectId();
        MongoDeTeste.banco().getCollection(colecao).insertOne(documento.append("_id", id));
        return id.toHexString();
    }

    private static String abrir(String turmaId, String modo, String salaId) {
        String sala = salaId == null ? "" : ",\"salaId\":\"" + salaId + "\"";
        return mensagem("sessao.abrir", "s1", "{\"turmaId\":\"%s\",\"modo\":\"%s\"%s}".formatted(turmaId, modo, sala));
    }

    private static String encerrar(String sessaoId) {
        return mensagem("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessaoId + "\"}");
    }

    private static String mensagem(String tipo, String id, String dados) {
        return "{\"v\":1,\"tipo\":\"%s\",\"id\":\"%s\",\"ts\":%d,\"dados\":%s}"
                .formatted(tipo, id, System.currentTimeMillis(), dados);
    }

    private static JsonNode ler(String texto) {
        return JSON.readTree(texto);
    }

    private static void assertErro(String texto, String codigoEsperado) {
        JsonNode resposta = ler(texto);
        assertEquals("erro", resposta.get("tipo").stringValue(), texto);
        assertEquals(codigoEsperado, resposta.get("dados").get("codigo").stringValue(), texto);
    }
}
