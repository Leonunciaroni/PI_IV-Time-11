/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.erro.RequisicaoInvalidaException;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regras do serviço isoladas do banco: os repositórios e o MongoTemplate são simulados. */
@ExtendWith(MockitoExtension.class)
class TurmaServiceTest {

    private static final String PROFESSOR = "prof-1";

    @Mock
    TurmaRepository turmas;

    @Mock
    MatriculaRepository matriculas;

    @Mock
    UsuarioRepository usuarios;

    @Mock
    MongoTemplate mongo;

    @InjectMocks
    TurmaService servico;

    // ---- Criar ----

    @Test
    void criaTurmaComHorarioNormalizadoEDadosSemEspacosNasPontas() {
        when(turmas.insert(any(Turma.class))).thenAnswer(chamada -> chamada.getArgument(0));

        TurmaResponse resposta = servico.criar(PROFESSOR,
                new TurmaRequest("  Turma A ", " Redes", "segunda 19:00"));

        ArgumentCaptor<Turma> gravada = ArgumentCaptor.forClass(Turma.class);
        verify(turmas).insert(gravada.capture());
        assertEquals("Turma A", gravada.getValue().nome());
        assertEquals("Redes", gravada.getValue().disciplina());
        assertEquals("Seg 19:00", gravada.getValue().horarioPadrao());
        assertEquals(PROFESSOR, gravada.getValue().professorId());
        assertEquals("Seg 19:00", resposta.horarioPadrao());
        assertEquals(0, resposta.totalAlunos());
    }

    @Test
    void naoGravaTurmaComHorarioForaDoPadrao() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> servico.criar(PROFESSOR, new TurmaRequest("Turma A", "Redes", "Seg 24:00")));

        verifyNoInteractions(turmas);
    }

    // ---- Listar ----

    @Test
    void listaVaziaNaoConsultaMatriculas() {
        when(turmas.findByProfessorId(PROFESSOR)).thenReturn(List.of());

        assertTrue(servico.listar(PROFESSOR).isEmpty());
        verifyNoInteractions(mongo);
    }

    @Test
    void listaOrdenadaPorDisciplinaENomeComOTotalDeCadaTurma() {
        when(turmas.findByProfessorId(PROFESSOR)).thenReturn(List.of(
                turma("t3", "Turma B", "estrutura de dados"),
                turma("t1", "Turma A", "Redes"),
                turma("t2", "Turma A", "Engenharia de Software")));
        Document contagem = new Document("_id", "t2").append("total", 2);
        when(mongo.aggregate(any(Aggregation.class), eq(Colecoes.MATRICULAS), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(contagem), new Document()));

        List<TurmaResponse> lista = servico.listar(PROFESSOR);

        assertEquals(List.of("t2", "t3", "t1"), lista.stream().map(TurmaResponse::id).toList());
        assertEquals(List.of(2L, 0L, 0L), lista.stream().map(TurmaResponse::totalAlunos).toList());
    }

    // ---- Detalhes e alunos ----

    @Test
    void turmaDeOutroProfessorOuInexistenteLancaNaoEncontrado() {
        when(turmas.findByIdAndProfessorId("t1", "prof-2")).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> servico.detalhar("prof-2", "t1"));
        assertThrows(RecursoNaoEncontradoException.class, () -> servico.alunos("prof-2", "t1"));
        assertThrows(RecursoNaoEncontradoException.class, () -> servico.candidatos("prof-2", "t1", null));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> servico.matricular("prof-2", "t1", new MatriculaRequest(List.of("a1"))));
        verifyNoInteractions(matriculas, usuarios);
    }

    @Test
    void detalhaTurmaComOTotalDeMatriculas() {
        when(turmas.findByIdAndProfessorId("t1", PROFESSOR)).thenReturn(Optional.of(turma("t1", "Turma A", "Redes")));
        when(matriculas.countByTurmaId("t1")).thenReturn(3L);

        assertEquals(3, servico.detalhar(PROFESSOR, "t1").totalAlunos());
    }

    @Test
    void alunosVemEmOrdemAlfabeticaSemDiferenciarMaiusculas() {
        when(turmas.findByIdAndProfessorId("t1", PROFESSOR)).thenReturn(Optional.of(turma("t1", "Turma A", "Redes")));
        when(matriculas.findByTurmaId("t1")).thenReturn(List.of(
                new Matricula("m1", "t1", "a1", null), new Matricula("m2", "t1", "a2", null)));
        when(usuarios.findAllById(List.of("a1", "a2"))).thenReturn(List.of(
                usuario("a1", "bruno Lima", Papel.ALUNO), usuario("a2", "Ana Souza", Papel.ALUNO)));

        List<AlunoResponse> alunos = servico.alunos(PROFESSOR, "t1");

        assertEquals(List.of("Ana Souza", "bruno Lima"), alunos.stream().map(AlunoResponse::nome).toList());
    }

    // ---- Candidatos ----

    @Test
    void candidatosSemBuscaENinguemMatriculadoFiltramSoPorPapelAluno() {
        when(turmas.findByIdAndProfessorId("t1", PROFESSOR)).thenReturn(Optional.of(turma("t1", "Turma A", "Redes")));
        when(matriculas.findByTurmaId("t1")).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(Usuario.class))).thenReturn(List.of());

        servico.candidatos(PROFESSOR, "t1", "   ");

        Document filtro = consultaDeCandidatos().getQueryObject();
        assertEquals(Papel.ALUNO, filtro.get("papel"));
        assertFalse(filtro.containsKey("id"), "sem matriculados não há exclusão por id");
        assertFalse(filtro.containsKey("$or"), "busca em branco não filtra por texto");
    }

    @Test
    void candidatosExcluemMatriculadosETratamABuscaComoTextoLiteral() {
        when(turmas.findByIdAndProfessorId("t1", PROFESSOR)).thenReturn(Optional.of(turma("t1", "Turma A", "Redes")));
        when(matriculas.findByTurmaId("t1")).thenReturn(List.of(new Matricula("m1", "t1", "a1", null)));
        when(mongo.find(any(Query.class), eq(Usuario.class)))
                .thenReturn(List.of(usuario("a2", "Carla Dias", Papel.ALUNO)));

        List<AlunoResponse> candidatos = servico.candidatos(PROFESSOR, "t1", " .* ");

        assertEquals(List.of("Carla Dias"), candidatos.stream().map(AlunoResponse::nome).toList());
        String filtro = consultaDeCandidatos().getQueryObject().toString();
        assertTrue(filtro.contains("a1"), "o aluno já matriculado deve ser excluído: " + filtro);
        // Pattern.quote envolve o texto em \Q...\E, que o MongoDB lê como literal
        assertTrue(filtro.contains("Q.*"), "a busca deve ser literal, não expressão regular: " + filtro);
    }

    // ---- Matricular ----

    @Test
    void matriculaOsNovosEIgnoraOsQueJaEstavamNaTurma() {
        when(turmas.findByIdAndProfessorId("t1", PROFESSOR)).thenReturn(Optional.of(turma("t1", "Turma A", "Redes")));
        when(usuarios.findAllById(Set.of("a1", "a2"))).thenReturn(List.of(
                usuario("a1", "Ana", Papel.ALUNO), usuario("a2", "Bruno", Papel.ALUNO)));
        when(matriculas.insert(any(Matricula.class))).thenAnswer(chamada -> {
            Matricula pedida = chamada.getArgument(0);
            if (pedida.alunoId().equals("a2")) {
                throw new DuplicateKeyException("turma_aluno_unico");
            }
            return pedida;
        });
        when(matriculas.countByTurmaId("t1")).thenReturn(5L);

        MatriculaResponse resposta = servico.matricular(PROFESSOR, "t1",
                new MatriculaRequest(List.of("a1", "a2", "a1")));

        assertEquals(new MatriculaResponse(1, 1, 5), resposta);
    }

    @Test
    void rejeitaAListaInteiraQuandoUmIdNaoEDeAluno() {
        when(turmas.findByIdAndProfessorId("t1", PROFESSOR)).thenReturn(Optional.of(turma("t1", "Turma A", "Redes")));
        when(usuarios.findAllById(Set.of("a1", "prof-2", "fantasma"))).thenReturn(List.of(
                usuario("a1", "Ana", Papel.ALUNO), usuario("prof-2", "Clara", Papel.PROFESSOR)));

        assertThrows(RequisicaoInvalidaException.class, () -> servico.matricular(PROFESSOR, "t1",
                new MatriculaRequest(List.of("a1", "prof-2", "fantasma"))));

        verify(matriculas, never()).insert(any(Matricula.class));
    }

    // ---- Apoio ----

    private Query consultaDeCandidatos() {
        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(consulta.capture(), eq(Usuario.class));
        return consulta.getValue();
    }

    private static Turma turma(String id, String nome, String disciplina) {
        return new Turma(id, nome, disciplina, "Seg 19:00", PROFESSOR, null);
    }

    private static Usuario usuario(String id, String nome, Papel papel) {
        return new Usuario(id, nome, id + "@puc.br", "hash", papel, "24000000", null);
    }
}
