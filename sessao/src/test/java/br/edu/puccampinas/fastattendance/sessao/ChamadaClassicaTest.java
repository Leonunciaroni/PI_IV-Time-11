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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/** {@code presenca.marcar} e {@code presenca.marcar_lote} pelo WebSocket, com o MongoDB real. */
class ChamadaClassicaTest {

    private static final String SEGREDO = "segredo-dos-testes-com-mais-de-32-bytes-0123456789";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final MongoCollection<Document> presencas = MongoDeTeste.banco().getCollection("presencas");
    private ServidorSessao servidor;

    private String professorId;
    private String turmaId;
    private String ana;
    private String bruno;
    private String carla;
    private String alunoDeOutraTurma;

    @BeforeEach
    void preparar() throws Exception {
        MongoDeTeste.limpar();
        professorId = new ObjectId().toHexString();
        turmaId = inserir("turmas", new Document("nome", "Turma A").append("professorId", professorId));
        ana = aluno("Ana Souza");
        bruno = aluno("Bruno Lima");
        carla = aluno("Carla Dias");
        alunoDeOutraTurma = aluno("Diego Alves");
        for (String alunoId : List.of(ana, bruno, carla)) {
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

    // ---- Marcação individual ----

    @Test
    void marcaAlunoPresenteERespondeComOsTotais() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");

        professor.enviar(marcar(sessaoId, ana, true));

        JsonNode resposta = ler(professor.proxima());
        assertEquals("presenca.atualizada", resposta.get("tipo").stringValue(), resposta.toString());
        assertEquals("m1", resposta.get("ref").stringValue());
        JsonNode dados = resposta.get("dados");
        assertEquals(sessaoId, dados.get("sessaoId").stringValue());
        assertEquals(ana, dados.get("alunoId").stringValue());
        assertEquals("Ana Souza", dados.get("nomeAluno").stringValue());
        assertTrue(dados.get("presente").booleanValue());
        assertEquals("MANUAL", dados.get("origem").stringValue());
        assertEquals(1, dados.get("totalPresentes").intValue());
        assertEquals(3, dados.get("totalMatriculados").intValue());

        Document gravada = presencas.find(eq("alunoId", ana)).first();
        assertEquals(sessaoId, gravada.getString("sessaoId"));
        assertEquals(turmaId, gravada.getString("turmaId"));
        assertTrue(gravada.getBoolean("presente"));
        assertEquals("MANUAL", gravada.getString("origem"));
        assertEquals(dados.get("registradoEm").longValue(), gravada.getDate("registradoEm").getTime());
    }

    @Test
    void desmarcaSemCriarSegundoRegistro() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");
        professor.enviar(marcar(sessaoId, ana, true));
        professor.proxima();

        professor.enviar(marcar(sessaoId, ana, false));

        JsonNode dados = ler(professor.proxima()).get("dados");
        assertFalse(dados.get("presente").booleanValue());
        assertEquals(0, dados.get("totalPresentes").intValue());
        assertEquals(1, presencas.countDocuments(eq("alunoId", ana)));
        assertFalse(presencas.find(eq("alunoId", ana)).first().getBoolean("presente"));
    }

    @Test
    void recusaAlunoNaoMatriculado() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");

        professor.enviar(marcar(sessaoId, alunoDeOutraTurma, true));
        assertErro(professor.proxima(), "ALUNO_NAO_MATRICULADO");

        professor.enviar(marcar(sessaoId, "nao-e-um-id", true));
        assertErro(professor.proxima(), "ALUNO_NAO_MATRICULADO");

        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void exigeOCampoPresente() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");

        professor.enviar(mensagem("presenca.marcar", "m1",
                "{\"sessaoId\":\"%s\",\"alunoId\":\"%s\"}".formatted(sessaoId, ana)));

        assertErro(professor.proxima(), "MENSAGEM_INVALIDA");
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Marcação em lote ----

    @Test
    void marcaTodosPresentesMenosAsExcecoes() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");

        professor.enviar(lote(sessaoId, true, List.of(bruno)));

        JsonNode resposta = ler(professor.proxima());
        assertEquals("presenca.lote_ok", resposta.get("tipo").stringValue(), resposta.toString());
        assertEquals("l1", resposta.get("ref").stringValue());
        assertEquals(2, resposta.get("dados").get("totalPresentes").intValue());
        assertEquals(3, resposta.get("dados").get("totalMatriculados").intValue());
        assertEquals(Map.of(ana, true, bruno, false, carla, true), situacao(sessaoId));
    }

    @Test
    void marcaTodosAusentesMenosAsExcecoes() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");

        professor.enviar(lote(sessaoId, false, List.of(carla)));

        assertEquals(1, ler(professor.proxima()).get("dados").get("totalPresentes").intValue());
        assertEquals(Map.of(ana, false, bruno, false, carla, true), situacao(sessaoId));
    }

    @Test
    void loteSobrescreveMarcacoesAnterioresSemDuplicar() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");
        professor.enviar(marcar(sessaoId, bruno, true));
        professor.proxima();

        professor.enviar(lote(sessaoId, true, List.of(bruno)));
        professor.proxima();

        assertEquals(3, presencas.countDocuments(eq("sessaoId", sessaoId)));
        assertEquals(Map.of(ana, true, bruno, false, carla, true), situacao(sessaoId));
    }

    @Test
    void loteComExcecaoDeForaDaTurmaNaoGravaNada() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");

        professor.enviar(lote(sessaoId, true, List.of(bruno, alunoDeOutraTurma)));

        assertErro(professor.proxima(), "ALUNO_NAO_MATRICULADO");
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void loteSemExcecoesMarcaTodos() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");

        professor.enviar(mensagem("presenca.marcar_lote", "l1",
                "{\"sessaoId\":\"%s\",\"presente\":true}".formatted(sessaoId)));

        assertEquals(3, ler(professor.proxima()).get("dados").get("totalPresentes").intValue());
    }

    // ---- Regras da sessão ----

    @Test
    void recusaMarcacaoForaDoModoClassico() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "SOM");

        professor.enviar(marcar(sessaoId, ana, true));
        assertErro(professor.proxima(), "MODO_INCOMPATIVEL");

        professor.enviar(lote(sessaoId, true, List.of()));
        assertErro(professor.proxima(), "MODO_INCOMPATIVEL");

        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void soOProfessorDaSessaoMarca() throws Exception {
        String sessaoId = abrirSessao(professor(), "CLASSICA");
        ClienteTeste outroProfessor = autenticado(new ObjectId().toHexString(), Papel.PROFESSOR);
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        outroProfessor.enviar(marcar(sessaoId, ana, true));
        assertErro(outroProfessor.proxima(), "PERMISSAO_NEGADA");

        aluno.enviar(marcar(sessaoId, ana, true));
        assertErro(aluno.proxima(), "PERMISSAO_NEGADA");

        aluno.enviar(lote(sessaoId, true, List.of()));
        assertErro(aluno.proxima(), "PERMISSAO_NEGADA");

        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void recusaSessaoInexistenteOuEncerrada() throws Exception {
        ClienteTeste professor = professor();
        professor.enviar(marcar(new ObjectId().toHexString(), ana, true));
        assertErro(professor.proxima(), "SESSAO_NAO_ENCONTRADA");

        String sessaoId = abrirSessao(professor, "CLASSICA");
        professor.enviar(mensagem("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessaoId + "\"}"));
        professor.proxima();

        professor.enviar(lote(sessaoId, true, List.of()));
        assertErro(professor.proxima(), "SESSAO_ENCERRADA");
    }

    @Test
    void encerramentoContaAsPresencasDaChamadaClassica() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, "CLASSICA");
        professor.enviar(lote(sessaoId, true, List.of(carla)));
        professor.proxima();

        professor.enviar(mensagem("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessaoId + "\"}"));

        assertEquals(2, ler(professor.proxima()).get("dados").get("totalPresentes").intValue());
    }

    // ---- Apoio ----

    private Map<String, Boolean> situacao(String sessaoId) {
        return presencas.find(eq("sessaoId", sessaoId)).into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(p -> p.getString("alunoId"), p -> p.getBoolean("presente")));
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

    private String abrirSessao(ClienteTeste professor, String modo) throws Exception {
        String sala = "";
        if (modo.equals("QRCODE")) {
            sala = ",\"salaId\":\"" + inserir("salas", new Document("nome", "H15")) + "\"";
        }
        professor.enviar(mensagem("sessao.abrir", "s1",
                "{\"turmaId\":\"%s\",\"modo\":\"%s\"%s}".formatted(turmaId, modo, sala)));
        JsonNode resposta = ler(professor.proxima());
        assertEquals("sessao.aberta", resposta.get("tipo").stringValue(), resposta.toString());
        return resposta.get("dados").get("sessaoId").stringValue();
    }

    private static String aluno(String nome) {
        return inserir("usuarios", new Document("nome", nome).append("papel", "ALUNO"));
    }

    private static String inserir(String colecao, Document documento) {
        ObjectId id = new ObjectId();
        MongoDeTeste.banco().getCollection(colecao).insertOne(documento.append("_id", id));
        return id.toHexString();
    }

    private static String marcar(String sessaoId, String alunoId, boolean presente) {
        return mensagem("presenca.marcar", "m1",
                "{\"sessaoId\":\"%s\",\"alunoId\":\"%s\",\"presente\":%s}".formatted(sessaoId, alunoId, presente));
    }

    private static String lote(String sessaoId, boolean presente, List<String> excecoes) {
        String lista = excecoes.stream().map(e -> "\"" + e + "\"").collect(Collectors.joining(","));
        return mensagem("presenca.marcar_lote", "l1",
                "{\"sessaoId\":\"%s\",\"presente\":%s,\"excecoes\":[%s]}".formatted(sessaoId, presente, lista));
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
