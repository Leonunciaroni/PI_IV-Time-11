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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
        String horario = HorarioPadrao.normalizar(requisicao.horarioPadrao())
                .orElseThrow(() -> new RequisicaoInvalidaException("Horário padrão inválido."));
        Turma nova = new Turma(null, requisicao.nome().strip(), requisicao.disciplina().strip(),
                horario, professorId, null);
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

    /**
     * Alunos que ainda não estão na turma, para o professor escolher quem matricular. A busca casa
     * com parte do nome ou da matrícula, sem diferenciar maiúsculas.
     */
    public List<AlunoResponse> candidatos(String professorId, String turmaId, String busca) {
        Turma turma = daProfessor(professorId, turmaId);

        Criteria criterio = Criteria.where("papel").is(Papel.ALUNO);
        List<String> jaMatriculados = matriculas.findByTurmaId(turma.id()).stream().map(Matricula::alunoId).toList();
        if (!jaMatriculados.isEmpty()) {
            criterio = criterio.and("id").nin(jaMatriculados);
        }
        if (busca != null && !busca.isBlank()) {
            // Pattern.quote trata o texto digitado como literal, não como expressão regular
            String texto = Pattern.quote(busca.strip());
            criterio = criterio.orOperator(
                    Criteria.where("nome").regex(texto, "i"),
                    Criteria.where("matricula").regex(texto, "i"));
        }
        Query consulta = Query.query(criterio).with(Sort.by("nome"));
        return mongo.find(consulta, Usuario.class).stream().map(AlunoResponse::de).toList();
    }

    /** Matricula vários alunos de uma vez; quem já está na turma é ignorado. */
    public MatriculaResponse matricular(String professorId, String turmaId, MatriculaRequest requisicao) {
        Turma turma = daProfessor(professorId, turmaId);

        Set<String> alunoIds = new LinkedHashSet<>(requisicao.alunoIds());
        long alunosEncontrados = usuarios.findAllById(alunoIds).stream()
                .filter(usuario -> usuario.papel() == Papel.ALUNO)
                .count();
        if (alunosEncontrados != alunoIds.size()) {
            throw new RequisicaoInvalidaException("A lista tem alunos que não existem.");
        }

        int matriculados = 0;
        for (String alunoId : alunoIds) {
            try {
                matriculas.insert(new Matricula(null, turma.id(), alunoId, null));
                matriculados++;
            } catch (DuplicateKeyException e) {
                // Já matriculado: o índice único turma_aluno_unico decide, mesmo com chamadas concorrentes
            }
        }
        return new MatriculaResponse(matriculados, alunoIds.size() - matriculados,
                matriculas.countByTurmaId(turma.id()));
    }

    /**
     * Tira o aluno da turma. As presenças já registradas continuam no banco, mas o aluno deixa de
     * aparecer na lista e nas estatísticas da turma.
     */
    public void removerMatricula(String professorId, String turmaId, String alunoId) {
        Turma turma = daProfessor(professorId, turmaId);
        if (matriculas.deleteByTurmaIdAndAlunoId(turma.id(), alunoId) == 0) {
            throw new RecursoNaoEncontradoException("Matrícula não encontrada");
        }
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
