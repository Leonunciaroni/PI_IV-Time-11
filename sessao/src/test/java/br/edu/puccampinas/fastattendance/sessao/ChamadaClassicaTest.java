/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresencaEmLote;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeMarcarPresencaEmLote;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/** {@code presenca.marcar} e {@code presenca.marcar_lote} pelo transporte por sockets, com o MongoDB real. */
class ChamadaClassicaTest {

    private static final String SEGREDO = ServidorDeTeste.SEGREDO;

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final MongoCollection<Document> presencas = MongoDeTeste.banco().getCollection("presencas");
    private ServidorDeTeste servidor;

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
        servidor = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        servidor.close();
    }

    // ---- Marcação individual ----

    @Test
    void marcaAlunoPresenteERespondeComOsTotais() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));

        RespostaDeMarcarPresenca resposta = professor.proximo(RespostaDeMarcarPresenca.class);
        assertEquals(sessaoId, resposta.getSessaoId());
        assertEquals(ana, resposta.getAlunoId());
        assertEquals("Ana Souza", resposta.getNomeAluno());
        assertTrue(resposta.isPresente());
        assertEquals("MANUAL", resposta.getOrigem().name());
        assertEquals(1, resposta.getTotalPresentes());
        assertEquals(3, resposta.getTotalMatriculados());

        Document gravada = presencas.find(eq("alunoId", ana)).first();
        assertEquals(sessaoId, gravada.getString("sessaoId"));
        assertEquals(turmaId, gravada.getString("turmaId"));
        assertTrue(gravada.getBoolean("presente"));
        assertEquals("MANUAL", gravada.getString("origem"));
        assertEquals(resposta.getRegistradoEm(), gravada.getDate("registradoEm").getTime());
    }

    @Test
    void desmarcaSemCriarSegundoRegistro() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);
        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));
        professor.proximo(RespostaDeMarcarPresenca.class);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, false));

        RespostaDeMarcarPresenca resposta = professor.proximo(RespostaDeMarcarPresenca.class);
        assertFalse(resposta.isPresente());
        assertEquals(0, resposta.getTotalPresentes());
        assertEquals(1, presencas.countDocuments(eq("alunoId", ana)));
        assertFalse(presencas.find(eq("alunoId", ana)).first().getBoolean("presente"));
    }

    @Test
    void recusaAlunoNaoMatriculado() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, alunoDeOutraTurma, true));
        professor.proximoErro(CodigoErro.ALUNO_NAO_MATRICULADO);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, "nao-e-um-id", true));
        professor.proximoErro(CodigoErro.ALUNO_NAO_MATRICULADO);

        assertEquals(0, presencas.countDocuments());
    }

    // ---- Marcação em lote ----

    @Test
    void marcaTodosPresentesMenosAsExcecoes() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);

        professor.enviar(lote(sessaoId, true, List.of(bruno)));

        RespostaDeMarcarPresencaEmLote resposta = professor.proximo(RespostaDeMarcarPresencaEmLote.class);
        assertEquals(2, resposta.getTotalPresentes());
        assertEquals(3, resposta.getTotalMatriculados());
        assertEquals(Map.of(ana, true, bruno, false, carla, true), situacao(sessaoId));
    }

    @Test
    void marcaTodosAusentesMenosAsExcecoes() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);

        professor.enviar(lote(sessaoId, false, List.of(carla)));

        assertEquals(1, professor.proximo(RespostaDeMarcarPresencaEmLote.class).getTotalPresentes());
        assertEquals(Map.of(ana, false, bruno, false, carla, true), situacao(sessaoId));
    }

    @Test
    void loteSobrescreveMarcacoesAnterioresSemDuplicar() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);
        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, bruno, true));
        professor.proximo(RespostaDeMarcarPresenca.class);

        professor.enviar(lote(sessaoId, true, List.of(bruno)));
        professor.proximo(RespostaDeMarcarPresencaEmLote.class);

        assertEquals(3, presencas.countDocuments(eq("sessaoId", sessaoId)));
        assertEquals(Map.of(ana, true, bruno, false, carla, true), situacao(sessaoId));
    }

    @Test
    void loteComExcecaoDeForaDaTurmaNaoGravaNada() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);

        professor.enviar(lote(sessaoId, true, List.of(bruno, alunoDeOutraTurma)));

        professor.proximoErro(CodigoErro.ALUNO_NAO_MATRICULADO);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void loteSemExcecoesMarcaTodos() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);

        professor.enviar(new PedidoDeMarcarPresencaEmLote(sessaoId, true, null));

        assertEquals(3, professor.proximo(RespostaDeMarcarPresencaEmLote.class).getTotalPresentes());
    }

    // ---- Regras da sessão ----

    @Test
    void recusaMarcacaoForaDoModoClassico() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.SOM);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));
        professor.proximoErro(CodigoErro.MODO_INCOMPATIVEL);

        professor.enviar(lote(sessaoId, true, List.of()));
        professor.proximoErro(CodigoErro.MODO_INCOMPATIVEL);

        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void soOProfessorDaSessaoMarca() throws Exception {
        String sessaoId = abrirSessao(professor(), ModoChamada.CLASSICA);
        ClienteTeste outroProfessor = autenticado(new ObjectId().toHexString(), Papel.PROFESSOR);
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        outroProfessor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));
        outroProfessor.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        aluno.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));
        aluno.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        aluno.enviar(lote(sessaoId, true, List.of()));
        aluno.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void recusaSessaoInexistenteOuEncerrada() throws Exception {
        ClienteTeste professor = professor();
        professor.enviar(new PedidoDeMarcarPresenca(new ObjectId().toHexString(), ana, true));
        professor.proximoErro(CodigoErro.SESSAO_NAO_ENCONTRADA);

        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);
        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professor.proximo(RespostaDeEncerrarSessao.class);

        professor.enviar(lote(sessaoId, true, List.of()));
        professor.proximoErro(CodigoErro.SESSAO_ENCERRADA);
    }

    @Test
    void encerramentoContaAsPresencasDaChamadaClassica() throws Exception {
        ClienteTeste professor = professor();
        String sessaoId = abrirSessao(professor, ModoChamada.CLASSICA);
        professor.enviar(lote(sessaoId, true, List.of(carla)));
        professor.proximo(RespostaDeMarcarPresencaEmLote.class);

        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));

        assertEquals(2, professor.proximo(RespostaDeEncerrarSessao.class).getTotalPresentes());
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
        ClienteTeste cliente = new ClienteTeste(servidor.porta());
        clientes.add(cliente);
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);
        return cliente;
    }

    private String abrirSessao(ClienteTeste professor, ModoChamada modo) throws Exception {
        professor.enviar(new PedidoDeAbrirSessao(turmaId, modo, null));
        return professor.proximo(RespostaDeAbrirSessao.class).getSessaoId();
    }

    private String aluno(String nome) {
        return inserir("usuarios", new Document("nome", nome).append("papel", "ALUNO"));
    }

    private static String inserir(String colecao, Document documento) {
        ObjectId id = new ObjectId();
        MongoDeTeste.banco().getCollection(colecao).insertOne(documento.append("_id", id));
        return id.toHexString();
    }

    private static PedidoDeMarcarPresencaEmLote lote(String sessaoId, boolean presente, List<String> excecoes) {
        return new PedidoDeMarcarPresencaEmLote(sessaoId, presente, new ArrayList<>(excecoes));
    }
}
