package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDePresencaAtualizada;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDeSessaoEncerrada;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code sessao.acompanhar} e os eventos {@code presenca.atualizada} e {@code sessao.encerrada}
 * alimentados pelos Change Streams do MongoDB (docs/protocolo-v1.md, seções 4.5 e 5.2).
 */
class PainelAoVivoTest {

    private static final String SEGREDO = ServidorDeTeste.SEGREDO;

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final List<ServidorDeTeste> servidores = new ArrayList<>();

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
        for (ServidorDeTeste servidor : servidores) {
            servidor.close();
        }
    }

    // ---- sessao.acompanhar ----

    @Test
    void acompanharDevolveSessaoEstadoComOsPresentesAtuais() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));
        professor.proximo(RespostaDeMarcarPresenca.class);

        ClienteTeste acompanhante = professor(porta(0));
        acompanhante.enviar(new PedidoDeAcompanharSessao(sessaoId));

        // Um evento da marcação acima pode chegar antes da resposta: ignora-se até sessao.estado
        RespostaDeAcompanharSessao resposta = aguardarTipo(acompanhante, RespostaDeAcompanharSessao.class);
        assertEquals(sessaoId, resposta.getSessaoId());
        assertEquals(ModoChamada.CLASSICA, resposta.getModo());
        assertTrue(resposta.getAbertaEm() > 0);
        assertEquals(3, resposta.getTotalMatriculados());
        assertEquals(1, resposta.getPresentes().size());
        assertEquals(ana, resposta.getPresentes().get(0).getAlunoId());
        assertEquals("Ana Souza", resposta.getPresentes().get(0).getNome());
        assertEquals("MANUAL", resposta.getPresentes().get(0).getOrigem().name());
        assertTrue(resposta.getPresentes().get(0).getRegistradoEm() > 0);
    }

    @Test
    void acompanharRecusaAlunoEOutroProfessor() throws Exception {
        String sessaoId = abrirSessao(professor(porta(0)));
        ClienteTeste aluno = autenticado(porta(0), ana, Papel.ALUNO);
        ClienteTeste outroProfessor = autenticado(porta(0), new ObjectId().toHexString(), Papel.PROFESSOR);

        aluno.enviar(new PedidoDeAcompanharSessao(sessaoId));
        aluno.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        outroProfessor.enviar(new PedidoDeAcompanharSessao(sessaoId));
        outroProfessor.proximoErro(CodigoErro.PERMISSAO_NEGADA);
    }

    @Test
    void acompanharRecusaSessaoInexistenteEEncerrada() throws Exception {
        ClienteTeste professor = professor(porta(0));
        professor.enviar(new PedidoDeAcompanharSessao(new ObjectId().toHexString()));
        professor.proximoErro(CodigoErro.SESSAO_NAO_ENCONTRADA);

        String sessaoId = abrirSessao(professor);
        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professor.proximo(RespostaDeEncerrarSessao.class);

        professor.enviar(new PedidoDeAcompanharSessao(sessaoId));
        professor.proximoErro(CodigoErro.SESSAO_ENCERRADA);
    }

    @Test
    void acompanharExigeOSessaoId() throws Exception {
        ClienteTeste professor = professor(porta(0));

        professor.enviar(new PedidoDeAcompanharSessao(null));

        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
    }

    // ---- presenca.atualizada ----

    @Test
    void professorAcompanhandoRecebePresencaGravadaPelaChamadaClassica() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        ClienteTeste acompanhante = professor(porta(0));
        acompanhar(acompanhante, sessaoId);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));

        // A resposta ao professor que marcou é de outra classe; o evento vai ao acompanhante
        professor.proximo(RespostaDeMarcarPresenca.class);
        EventoDePresencaAtualizada evento = acompanhante.proximo(EventoDePresencaAtualizada.class);
        assertEquals(sessaoId, evento.getSessaoId());
        assertEquals(ana, evento.getAlunoId());
        assertEquals("Ana Souza", evento.getNomeAluno());
        assertTrue(evento.isPresente());
        assertEquals("MANUAL", evento.getOrigem().name());
        assertEquals(1, evento.getTotalPresentes());
        assertEquals(3, evento.getTotalMatriculados());
    }

    @Test
    void desmarcarTambemGeraEvento() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        ClienteTeste acompanhante = professor(porta(0));
        acompanhar(acompanhante, sessaoId);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));
        professor.proximo(RespostaDeMarcarPresenca.class);
        assertTrue(aguardarTipo(acompanhante, EventoDePresencaAtualizada.class).isPresente());

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, false));
        professor.proximo(RespostaDeMarcarPresenca.class);

        EventoDePresencaAtualizada evento = aguardarTipo(acompanhante, EventoDePresencaAtualizada.class);
        assertFalse(evento.isPresente());
        assertEquals(0, evento.getTotalPresentes());
    }

    @Test
    void quemNaoAcompanhaNaoRecebeEvento() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        ClienteTeste naoAcompanha = professor(porta(0));
        ClienteTeste aluno = autenticado(porta(0), ana, Papel.ALUNO);

        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, ana, true));
        professor.proximo(RespostaDeMarcarPresenca.class);

        assertNull(naoAcompanha.aguardar(500));
        assertNull(aluno.aguardar(200));
    }

    @Test
    void encerramentoEnviaSessaoEncerradaAoAcompanhante() throws Exception {
        ClienteTeste professor = professor(porta(0));
        String sessaoId = abrirSessao(professor);
        professor.enviar(new PedidoDeMarcarPresenca(sessaoId, carla, true));
        professor.proximo(RespostaDeMarcarPresenca.class);
        ClienteTeste acompanhante = professor(porta(0));
        acompanhar(acompanhante, sessaoId);

        professor.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professor.proximo(RespostaDeEncerrarSessao.class);

        EventoDeSessaoEncerrada evento = aguardarTipo(acompanhante, EventoDeSessaoEncerrada.class);
        assertEquals("PROFESSOR", evento.getMotivo().name());
        assertEquals(1, evento.getTotalPresentes());
    }

    // ---- Duas réplicas do servidor de sessão ----

    @Test
    void presencaGravadaNaReplicaAChegaAoProfessorNaReplicaB() throws Exception {
        ServidorDeTeste replicaB = iniciarServidor();
        servidores.add(replicaB);
        int portaA = porta(0);
        int portaB = replicaB.porta();

        ClienteTeste professorA = professor(portaA);
        String sessaoId = abrirSessao(professorA);
        ClienteTeste acompanhanteB = professor(portaB);
        acompanhar(acompanhanteB, sessaoId);

        professorA.enviar(new PedidoDeMarcarPresenca(sessaoId, bruno, true));
        professorA.proximo(RespostaDeMarcarPresenca.class);

        EventoDePresencaAtualizada evento = acompanhanteB.proximo(EventoDePresencaAtualizada.class);
        assertEquals(bruno, evento.getAlunoId());
        assertEquals(1, evento.getTotalPresentes());
        // Uma única entrega: a réplica B não repete o evento
        assertNull(acompanhanteB.aguardar(500));
    }

    @Test
    void encerramentoFeitoNaReplicaAAvisaAcompanhanteNaReplicaB() throws Exception {
        ServidorDeTeste replicaB = iniciarServidor();
        servidores.add(replicaB);

        ClienteTeste professorA = professor(porta(0));
        String sessaoId = abrirSessao(professorA);
        ClienteTeste acompanhanteB = professor(replicaB.porta());
        acompanhar(acompanhanteB, sessaoId);

        professorA.enviar(new PedidoDeEncerrarSessao(sessaoId));
        professorA.proximo(RespostaDeEncerrarSessao.class);

        aguardarTipo(acompanhanteB, EventoDeSessaoEncerrada.class);
    }

    // ---- Apoio ----

    private ServidorDeTeste iniciarServidor() throws Exception {
        return new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    private int porta(int indice) {
        return servidores.get(indice).porta();
    }

    private ClienteTeste professor(int porta) throws Exception {
        return autenticado(porta, professorId, Papel.PROFESSOR);
    }

    private ClienteTeste autenticado(int porta, String usuarioId, Papel papel) throws Exception {
        ClienteTeste cliente = new ClienteTeste(porta);
        clientes.add(cliente);
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);
        return cliente;
    }

    private void acompanhar(ClienteTeste cliente, String sessaoId) throws Exception {
        cliente.enviar(new PedidoDeAcompanharSessao(sessaoId));
        aguardarTipo(cliente, RespostaDeAcompanharSessao.class);
    }

    /** Lê os comunicados até chegar ao tipo esperado; eventos que chegam antes da resposta são ignorados. */
    private static <T extends Comunicado> T aguardarTipo(ClienteTeste cliente, Class<T> tipo) {
        while (true) {
            Comunicado recebido = cliente.proximo();
            if (tipo.isInstance(recebido)) {
                return tipo.cast(recebido);
            }
        }
    }

    private String abrirSessao(ClienteTeste professor) throws Exception {
        professor.enviar(new PedidoDeAbrirSessao(turmaId, ModoChamada.CLASSICA, null));
        return professor.proximo(RespostaDeAbrirSessao.class).getSessaoId();
    }

    private static String aluno(String nome) {
        return inserir("usuarios", new Document("nome", nome).append("papel", "ALUNO"));
    }

    private static String inserir(String colecao, Document documento) {
        ObjectId id = new ObjectId();
        MongoDeTeste.banco().getCollection(colecao).insertOne(documento.append("_id", id));
        return id.toHexString();
    }
}
