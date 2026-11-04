package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.servidor.ServidorSessao;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code sessao.acompanhar} e os eventos {@code presenca.atualizada} e {@code sessao.encerrada}
 * alimentados pelos Change Streams do MongoDB (docs/protocolo-v1.md, seções 4.5 e 5.2).
 */
class PainelAoVivoTest {

    private static final String SEGREDO = "segredo-dos-testes-com-mais-de-32-bytes-0123456789";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final List<ServidorSessao> servidores = new ArrayList<>();

    private String professorId;
    private String turmaId;
    private String ana;
    private String bruno;
    private String carla;

    @BeforeEach
    void preparar() throws Exception {
        MongoDeTeste.limpar();
        professorId = new ObjectId().toHexString();
        turmaId = inserir("turmas", new Document("nome", "Turma A").append("professorId", professorId));
        ana = aluno("Ana Souza");
        bruno = aluno("Bruno Lima");
        carla = aluno("Carla Dias");
        for (String alunoId : List.of(ana, bruno, carla)) {
            inserir("matriculas", new Document("turmaId", turmaId).append("alunoId", alunoId));
        }
        servidores.add(iniciarServidor());
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        for (ServidorSessao servidor : servidores) {
            servidor.encerrar(1_000);
        }
    }

    // ---- sessao.acompanhar ----

    @Test
    void acompanharDevolveSessaoEstadoComOsPresentesAtuais() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        professor.enviar(marcar(sessaoId, ana, true));
        professor.proxima();

        ClienteTeste acompanhante = professor(porta(0));
        acompanhante.enviar(mensagem("sessao.acompanhar", "c1", "{\"sessaoId\":\"%s\"}".formatted(sessaoId)));

        // Um evento da marcação acima pode chegar antes da resposta: ignora-se até sessao.estado
        JsonNode resposta = aguardarTipo(acompanhante, "sessao.estado");
        assertEquals("c1", resposta.get("ref").stringValue());
        JsonNode dados = resposta.get("dados");
        assertEquals(sessaoId, dados.get("sessaoId").stringValue());
        assertEquals("CLASSICA", dados.get("modo").stringValue());
        assertTrue(dados.get("abertaEm").longValue() > 0);
        assertEquals(3, dados.get("totalMatriculados").intValue());
        JsonNode presentes = dados.get("presentes");
        assertEquals(1, presentes.size());
        assertEquals(ana, presentes.get(0).get("alunoId").stringValue());
        assertEquals("Ana Souza", presentes.get(0).get("nome").stringValue());
        assertEquals("MANUAL", presentes.get(0).get("origem").stringValue());
        assertTrue(presentes.get(0).get("registradoEm").longValue() > 0);
    }

    @Test
    void acompanharRecusaAlunoEOutroProfessor() throws Exception {
        String sessaoId = abrirSessao(professor(porta(0)));
        ClienteTeste aluno = autenticado(porta(0), ana, Papel.ALUNO);
        ClienteTeste outroProfessor = autenticado(porta(0), new ObjectId().toHexString(), Papel.PROFESSOR);

        aluno.enviar(mensagem("sessao.acompanhar", "c1", "{\"sessaoId\":\"%s\"}".formatted(sessaoId)));
        assertErro(aluno.proxima(), "PERMISSAO_NEGADA");

        outroProfessor.enviar(mensagem("sessao.acompanhar", "c1", "{\"sessaoId\":\"%s\"}".formatted(sessaoId)));
        assertErro(outroProfessor.proxima(), "PERMISSAO_NEGADA");
    }

    @Test
    void acompanharRecusaSessaoInexistenteEEncerrada() throws Exception {
        ClienteTeste professor = professor(porta(0));
        professor.enviar(mensagem("sessao.acompanhar", "c1",
                "{\"sessaoId\":\"%s\"}".formatted(new ObjectId().toHexString())));
        assertErro(professor.proxima(), "SESSAO_NAO_ENCONTRADA");

        String sessaoId = abrirSessao(professor);
        professor.enviar(mensagem("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessaoId + "\"}"));
        professor.proxima();

        professor.enviar(mensagem("sessao.acompanhar", "c2", "{\"sessaoId\":\"%s\"}".formatted(sessaoId)));
        assertErro(professor.proxima(), "SESSAO_ENCERRADA");
    }

    @Test
    void acompanharExigeOSessaoId() throws Exception {
        ClienteTeste professor = professor(porta(0));

        professor.enviar(mensagem("sessao.acompanhar", "c1", "{}"));

        assertErro(professor.proxima(), "MENSAGEM_INVALIDA");
    }

    // ---- presenca.atualizada ----

    @Test
    void professorAcompanhandoRecebePresencaGravadaPelaChamadaClassica() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        ClienteTeste acompanhante = professor(porta(0));
        acompanhar(acompanhante, sessaoId);

        professor.enviar(marcar(sessaoId, ana, true));

        // A resposta ao professor que marcou tem ref; o evento aos acompanhantes não tem
        assertEquals("m1", ler(professor.proxima()).get("ref").stringValue());
        JsonNode evento = ler(acompanhante.proxima());
        assertEquals("presenca.atualizada", evento.get("tipo").stringValue(), evento.toString());
        assertNull(evento.get("ref"));
        JsonNode dados = evento.get("dados");
        assertEquals(sessaoId, dados.get("sessaoId").stringValue());
        assertEquals(ana, dados.get("alunoId").stringValue());
        assertEquals("Ana Souza", dados.get("nomeAluno").stringValue());
        assertTrue(dados.get("presente").booleanValue());
        assertEquals("MANUAL", dados.get("origem").stringValue());
        assertEquals(1, dados.get("totalPresentes").intValue());
        assertEquals(3, dados.get("totalMatriculados").intValue());
    }

    @Test
    void desmarcarTambemGeraEvento() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        ClienteTeste acompanhante = professor(porta(0));
        acompanhar(acompanhante, sessaoId);

        professor.enviar(marcar(sessaoId, ana, true));
        professor.proxima();
        assertTrue(aguardarTipo(acompanhante, "presenca.atualizada").get("dados").get("presente").booleanValue());

        professor.enviar(marcar(sessaoId, ana, false));
        professor.proxima();

        JsonNode dados = aguardarTipo(acompanhante, "presenca.atualizada").get("dados");
        assertFalse(dados.get("presente").booleanValue());
        assertEquals(0, dados.get("totalPresentes").intValue());
    }

    @Test
    void quemNaoAcompanhaNaoRecebeEvento() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        ClienteTeste naoAcompanha = professor(porta(0));
        ClienteTeste aluno = autenticado(porta(0), ana, Papel.ALUNO);

        professor.enviar(marcar(sessaoId, ana, true));
        professor.proxima();

        assertNull(naoAcompanha.aguardar(500));
        assertNull(aluno.aguardar(0));
    }

    @Test
    void encerramentoEnviaSessaoEncerradaAoAcompanhante() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        professor.enviar(marcar(sessaoId, carla, true));
        professor.proxima();
        ClienteTeste acompanhante = professor(porta(0));
        acompanhar(acompanhante, sessaoId);

        professor.enviar(mensagem("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessaoId + "\"}"));
        professor.proxima();

        JsonNode evento = aguardarTipo(acompanhante, "sessao.encerrada");
        assertNull(evento.get("ref"));
        assertEquals("PROFESSOR", evento.get("dados").get("motivo").stringValue());
        assertEquals(1, evento.get("dados").get("totalPresentes").intValue());
    }

    // ---- Duas réplicas do servidor de sessão ----

    @Test
    void presencaGravadaNaReplicaAChegaAoProfessorNaReplicaB() throws Exception {
        ServidorSessao replicaB = iniciarServidor();
        servidores.add(replicaB);
        int portaA = porta(0);
        int portaB = replicaB.getPort();

        ClienteTeste professorA = professor(portaA);
        String sessaoId = abrirSessao(professorA);
        ClienteTeste acompanhanteB = professor(portaB);
        acompanhar(acompanhanteB, sessaoId);

        professorA.enviar(marcar(sessaoId, bruno, true));
        assertEquals("m1", ler(professorA.proxima()).get("ref").stringValue());

        JsonNode evento = ler(acompanhanteB.proxima());
        assertEquals("presenca.atualizada", evento.get("tipo").stringValue(), evento.toString());
        assertEquals(bruno, evento.get("dados").get("alunoId").stringValue());
        assertEquals(1, evento.get("dados").get("totalPresentes").intValue());
        // Uma única entrega: a réplica B não repete o evento
        assertNull(acompanhanteB.aguardar(500));
    }

    @Test
    void encerramentoFeitoNaReplicaAAvisaAcompanhanteNaReplicaB() throws Exception {
        ServidorSessao replicaB = iniciarServidor();
        servidores.add(replicaB);

        ClienteTeste professorA = professor(porta(0));
        String sessaoId = abrirSessao(professorA);
        ClienteTeste acompanhanteB = professor(replicaB.getPort());
        acompanhar(acompanhanteB, sessaoId);

        professorA.enviar(mensagem("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessaoId + "\"}"));
        professorA.proxima();

        assertEquals("sessao.encerrada", aguardarTipo(acompanhanteB, "sessao.encerrada").get("tipo").stringValue());
    }

    // ---- Apoio ----

    private ServidorSessao iniciarServidor() throws Exception {
        return SessaoApplication.iniciar(new Configuracao(0, SEGREDO, MongoDeTeste.uri(),
                Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofHours(4)));
    }

    private int porta(int indice) {
        return servidores.get(indice).getPort();
    }

    private ClienteTeste professor(int porta) throws Exception {
        return autenticado(porta, professorId, Papel.PROFESSOR);
    }

    private ClienteTeste autenticado(int porta, String usuarioId, Papel papel) throws Exception {
        ClienteTeste cliente = new ClienteTeste(porta);
        clientes.add(cliente);
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(mensagem("auth", "a", "{\"token\":\"" + token + "\"}"));
        assertEquals("auth.ok", ler(cliente.proxima()).get("tipo").stringValue());
        return cliente;
    }

    private void acompanhar(ClienteTeste cliente, String sessaoId) throws Exception {
        cliente.enviar(mensagem("sessao.acompanhar", "c0", "{\"sessaoId\":\"%s\"}".formatted(sessaoId)));
        assertEquals("sessao.estado", aguardarTipo(cliente, "sessao.estado").get("tipo").stringValue());
    }

    /** Lê as mensagens até chegar a do tipo esperado; eventos que chegam antes da resposta são ignorados. */
    private static JsonNode aguardarTipo(ClienteTeste cliente, String tipo) throws Exception {
        while (true) {
            JsonNode mensagem = ler(cliente.proxima());
            if (tipo.equals(mensagem.get("tipo").stringValue())) {
                return mensagem;
            }
        }
    }

    private String abrirSessao(ClienteTeste professor) throws Exception {
        professor.enviar(mensagem("sessao.abrir", "s1",
                "{\"turmaId\":\"%s\",\"modo\":\"CLASSICA\"}".formatted(turmaId)));
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
