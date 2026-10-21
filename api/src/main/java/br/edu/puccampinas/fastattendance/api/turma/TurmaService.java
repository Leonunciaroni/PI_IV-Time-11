package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TurmaService {

    private static final Comparator<Turma> ORDEM = Comparator
            .comparing(Turma::disciplina, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Turma::nome, String.CASE_INSENSITIVE_ORDER);

    private final TurmaRepository turmas;
    private final MatriculaRepository matriculas;
    private final UsuarioRepository usuarios;
    private final MongoTemplate mongo;

    public TurmaService(TurmaRepository turmas, MatriculaRepository matriculas, UsuarioRepository usuarios,
                        MongoTemplate mongo) {
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.usuarios = usuarios;
        this.mongo = mongo;
    }

    public TurmaResponse criar(String professorId, TurmaRequest requisicao) {
        Turma nova = new Turma(null, requisicao.nome().strip(), requisicao.disciplina().strip(),
                requisicao.horarioPadrao().strip(), professorId, null);
        return TurmaResponse.de(turmas.insert(nova), 0);
    }

    /** Turmas do professor, ordenadas por disciplina e nome. */
    public List<TurmaResponse> listar(String professorId) {
        List<Turma> doProfessor = turmas.findByProfessorId(professorId).stream().sorted(ORDEM).toList();
        Map<String, Long> totais = totaisPorTurma(doProfessor);
        return doProfessor.stream()
                .map(turma -> TurmaResponse.de(turma, totais.getOrDefault(turma.id(), 0L)))
                .toList();
    }

    public TurmaResponse detalhar(String professorId, String turmaId) {
        Turma turma = daProfessor(professorId, turmaId);
        return TurmaResponse.de(turma, matriculas.countByTurmaId(turma.id()));
    }

    /** Alunos matriculados na turma, em ordem alfabética. */
    public List<AlunoResponse> alunos(String professorId, String turmaId) {
        Turma turma = daProfessor(professorId, turmaId);
        List<String> alunoIds = matriculas.findByTurmaId(turma.id()).stream().map(Matricula::alunoId).toList();
        return usuarios.findAllById(alunoIds).stream()
                .sorted(Comparator.comparing(Usuario::nome, String.CASE_INSENSITIVE_ORDER))
                .map(AlunoResponse::de)
                .toList();
    }

    /** A turma de outro professor responde como inexistente, para não revelar que ela existe. */
    private Turma daProfessor(String professorId, String turmaId) {
        return turmas.findByIdAndProfessorId(turmaId, professorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada"));
    }

    /** Conta as matrículas de todas as turmas em uma única consulta, em vez de uma por turma. */
    private Map<String, Long> totaisPorTurma(List<Turma> doProfessor) {
        Map<String, Long> totais = new HashMap<>();
        if (doProfessor.isEmpty()) {
            return totais;
        }
        List<String> ids = doProfessor.stream().map(Turma::id).toList();
        Aggregation agregacao = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("turmaId").in(ids)),
                Aggregation.group("turmaId").count().as("total"));
        for (Document linha : mongo.aggregate(agregacao, Colecoes.MATRICULAS, Document.class)) {
            totais.put(linha.getString("_id"), ((Number) linha.get("total")).longValue());
        }
        return totais;
    }
}
