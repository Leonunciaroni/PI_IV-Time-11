package br.edu.puccampinas.fastattendance.api.aula;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.PresencaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.SessaoRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AulaService {
    private final TurmaRepository turmas;
    private final SessaoRepository sessoes;
    private final PresencaRepository presencas;
    private final MatriculaRepository matriculas;

    public AulaService(TurmaRepository turmas, SessaoRepository sessoes,
                       PresencaRepository presencas, MatriculaRepository matriculas) {
        this.turmas = turmas;
        this.sessoes = sessoes;
        this.presencas = presencas;
        this.matriculas = matriculas;
    }

    public List<AulaResponse> aulas(String professorId, String turmaId) {
        turmas.findByIdAndProfessorId(turmaId, professorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada"));
        var totais = presencas.findByTurmaId(turmaId).stream().filter(Presenca::presente)
                .collect(Collectors.groupingBy(Presenca::sessaoId, Collectors.counting()));
        return sessoes.findByTurmaId(turmaId).stream()
                .sorted(Comparator.comparing(Sessao::abertaEm).reversed().thenComparing(Sessao::id))
                .map(s -> new AulaResponse(s.id(), s.turmaId(), s.modo(), s.status(), s.abertaEm(),
                        s.encerradaEm(), totais.getOrDefault(s.id(), 0L))).toList();
    }

    public List<SessaoAtivaResponse> ativas(String alunoId) {
        var ids = matriculas.findByAlunoId(alunoId).stream().map(Matricula::turmaId).distinct().toList();
        if (ids.isEmpty()) return List.of();
        var porId = turmas.findAllById(ids).stream().collect(Collectors.toMap(Turma::id, Function.identity()));
        return sessoes.findByTurmaIdInAndStatus(ids, StatusSessao.ABERTA).stream()
                .filter(s -> porId.containsKey(s.turmaId()))
                .sorted(Comparator.comparing(Sessao::abertaEm).reversed().thenComparing(Sessao::id))
                .map(s -> {
                    Turma turma = porId.get(s.turmaId());
                    return new SessaoAtivaResponse(s.id(), turma.id(), turma.nome(), turma.disciplina(),
                            s.modo(), s.abertaEm());
                }).toList();
    }
}
