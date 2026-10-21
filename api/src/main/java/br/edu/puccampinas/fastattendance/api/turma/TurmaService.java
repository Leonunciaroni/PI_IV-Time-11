package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
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
    private final MongoTemplate mongo;

    public TurmaService(TurmaRepository turmas, MongoTemplate mongo) {
        this.turmas = turmas;
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
