package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/** {@code sessao.abrir} e {@code sessao.encerrar} pelo transporte por sockets, com o MongoDB real. */
class SessaoAbrirEncerrarTest {

    private static final String SEGREDO = ServidorDeTeste.SEGREDO;

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final MongoCollection<Document> sessoes = MongoDeTeste.banco().getCollection("sessoes");
    private ServidorDeTeste servidor;

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
        servidor = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofHours(4));
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        servidor.close();
    }

    // ---- Abertura ----

    @Test
    void abreSessaoEEnviaAConfiguracaoTotpAoProfessor() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.CLASSICA, null));

        RespostaDeAbrirSessao resposta = professor.proximo(RespostaDeAbrirSessao.class);
        assertEquals(turmaId, resposta.getTurmaId());
        assertEquals(ModoChamada.CLASSICA, resposta.getModo());
        assertTrue(resposta.getAbertaEm() > 0);
        assertTrue(resposta.getTotp().getSegredo().matches("[A-Z2-7]{32}"));
        assertEquals("HmacSHA256", resposta.getTotp().getAlgoritmo());
        assertEquals(6, resposta.getTotp().getDigitos());
        assertEquals(15, resposta.getTotp().getPeriodoSegundos());
        assertEquals(0, resposta.getTotp().getT0());

        Document gravada = sessoes.find(eq("_id", new ObjectId(resposta.getSessaoId()))).first();
        assertNotNull(gravada);
        assertEquals("ABERTA", gravada.getString("status"));
        assertEquals(professorId, gravada.getString("professorId"));
        assertEquals(resposta.getTotp().getSegredo(), gravada.getString("segredoTotp"));
    }

    @Test
    void abreSessaoQrCodeComSala() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.QRCODE, salaId));

        professor.proximo(RespostaDeAbrirSessao.class);
        assertEquals(salaId, sessoes.find().first().getString("salaId"));
    }

    @Test
    void modoQrCodeExigeSalaExistente() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.QRCODE, null));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);

        professor.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.QRCODE, new ObjectId().toHexString()));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);

        assertEquals(0, sessoes.countDocuments());
    }

    @Test
    void recusaPedidoSemTurma() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(new PedidoDeAbrirSessao(null, ModoChamada.CLASSICA, null));

        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
    }

    @Test
    void soODonoDaTurmaAbreSessao() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(new PedidoDeAbrirSessao(turmaDeOutroProfessor, ModoChamada.CLASSICA, null));
        professor.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        professor.enviar(new PedidoDeAbrirSessao(new ObjectId().toHexString(), ModoChamada.CLASSICA, null));
        professor.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        professor.enviar(new PedidoDeAbrirSessao("nao-e-um-objectid", ModoChamada.CLASSICA, null));
        professor.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        assertEquals(0, sessoes.countDocuments());
    }

    @Test
    void alunoNaoAbreSessao() throws Exception {
        ClienteTeste aluno = autenticado(new ObjectId().toHexString(), Papel.ALUNO);

        aluno.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.CLASSICA, null));

        aluno.proximoErro(CodigoErro.PERMISSAO_NEGADA);
    }

    @Test
    void recusaSegundaSessaoAbertaNaMesmaTurma() throws Exception {
        ClienteTeste professor = professor();
        abrirSessao(professor);

        professor.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.SOM, null));

        professor.proximoErro(CodigoErro.SESSAO_JA_ABERTA);
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

        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));

        RespostaDeEncerrarSessao resposta = professor.proximo(RespostaDeEncerrarSessao.class);
        assertEquals(sessaoId, resposta.getSessaoId());
        assertEquals("PROFESSOR", resposta.getMotivo().name());
        assertEquals(2, resposta.getTotalPresentes());

        Document gravada = sessoes.find(eq("_id", new ObjectId(sessaoId))).first();
        assertEquals("ENCERRADA", gravada.getString("status"));
        assertEquals("PROFESSOR", gravada.getString("motivoEncerramento"));
        assertEquals(resposta.getEncerradaEm(), gravada.getDate("encerradaEm").getTime());
    }

    @Test
    void permiteNovaSessaoDepoisDoEncerramento() throws Exception {
        ClienteTeste professor = professor();
        String primeira = abrirSessao(professor);
        professor.enviar(new PedidoDeEncerrarSessao(primeira));
        professor.proximo(RespostaDeEncerrarSessao.class);

        String segunda = abrirSessao(professor);

        assertNotEquals(primeira, segunda);
        assertNotEquals(sessoes.find(eq("_id", new ObjectId(primeira))).first().getString("segredoTotp"),
                sessoes.find(eq("_id", new ObjectId(segunda))).first().getString("segredoTotp"));
    }

    @Test
    void naoEncerraDuasVezesNemSessaoInexistente() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor);
        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professor.proximo(RespostaDeEncerrarSessao.class);

        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professor.proximoErro(CodigoErro.SESSAO_ENCERRADA);

        professor.enviar(new PedidoDeEncerrarSessao(new ObjectId().toHexString()));
        professor.proximoErro(CodigoErro.SESSAO_NAO_ENCONTRADA);
    }

    @Test
    void soODonoEncerraASessao() throws Exception {
        String sessaoId = abrirSessao(professor());
        ClienteTeste outro = autenticado(new ObjectId().toHexString(), Papel.PROFESSOR);

        outro.enviar(new PedidoDeEncerrarSessao(sessaoId));

        outro.proximoErro(CodigoErro.PERMISSAO_NEGADA);
        assertEquals("ABERTA", sessoes.find().first().getString("status"));
    }

    @Test
    void nenhumRegistroEAceitoAposOEncerramento() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor);
        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professor.proximo(RespostaDeEncerrarSessao.class);
        ClienteTeste aluno = autenticado(new ObjectId().toHexString(), Papel.ALUNO);

        aluno.enviar(new PedidoDeCheckin(sessaoId, "123456", OrigemPresenca.QRCODE, "d1", null, null, null));
        aluno.proximoErro(CodigoErro.SESSAO_ENCERRADA);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, "a1", true));
        professor.proximoErro(CodigoErro.SESSAO_ENCERRADA);
    }

    // ---- Tempo limite ----

    @Test
    void encerraPorTempoLimiteSemAcaoDoProfessor() throws Exception {
        servidor.close();
        clientes.clear();
        servidor = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofMillis(500));
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor);

        Document gravada = aguardarStatus(sessaoId, "ENCERRADA", 5_000);

        assertEquals("TEMPO_LIMITE", gravada.getString("motivoEncerramento"));
        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professor.proximoErro(CodigoErro.SESSAO_ENCERRADA);
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

    private ClienteTeste professor() throws Exception {
        return autenticado(professorId, Papel.PROFESSOR);
    }

    private ClienteTeste autenticado(String usuarioId, Papel papel) throws Exception {
        ClienteTeste cliente = new ClienteTeste(servidor.porta());
        clientes.add(cliente);
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);
        return cliente;
    }

    private String abrirSessao(ClienteTeste professor) throws Exception {
        professor.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.CLASSICA, null));
        return professor.proximo(RespostaDeAbrirSessao.class).getSessaoId();
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
}
