package br.edu.puccampinas.fastattendance.api.aluno;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.estatistica.CalculadoraDeHistorico;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.PresencaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.SessaoRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InicioAlunoService {

    private final MatriculaRepository matriculas;
    private final TurmaRepository turmas;
    private final SessaoRepository sessoes;
    private final PresencaRepository presencas;
    private final UsuarioRepository usuarios;

    public InicioAlunoService(MatriculaRepository matriculas, TurmaRepository turmas,
                              SessaoRepository sessoes, PresencaRepository presencas,
                              UsuarioRepository usuarios) {
        this.matriculas = matriculas;
        this.turmas = turmas;
        this.sessoes = sessoes;
        this.presencas = presencas;
        this.usuarios = usuarios;
    }

    public List<TurmaDoAlunoResponse> listar(String alunoId) {
        var aluno = usuarios.findById(alunoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno não encontrado"));
        var ids = matriculas.findByAlunoId(alunoId).stream().map(Matricula::turmaId).distinct().toList();
        if (ids.isEmpty()) return List.of();

        // Consultas em lote: não busca aulas e presenças novamente para cada cartão da tela.
        var aulasPorTurma = sessoes.findByTurmaIdIn(ids).stream().collect(Collectors.groupingBy(Sessao::turmaId));
        var presencasPorTurma = presencas.findByAlunoIdAndTurmaIdIn(alunoId, ids).stream()
                .collect(Collectors.groupingBy(Presenca::turmaId));
        return turmas.findAllById(ids).stream()
                .sorted(Comparator.comparing(Turma::disciplina).thenComparing(Turma::nome))
                .map(turma -> {
                    var aulas = aulasPorTurma.getOrDefault(turma.id(), List.of());
                    var historico = CalculadoraDeHistorico.calcular(turma.id(), aluno,
                            aulas.stream().filter(a -> a.status() == StatusSessao.ENCERRADA).toList(),
                            presencasPorTurma.getOrDefault(turma.id(), List.of()));
                    var aberta = aulas.stream().filter(a -> a.status() == StatusSessao.ABERTA)
                            .max(Comparator.comparing(Sessao::abertaEm))
                            .map(a -> new TurmaDoAlunoResponse.ChamadaAberta(a.id(), a.modo(), a.abertaEm()))
                            .orElse(null);
                    return new TurmaDoAlunoResponse(turma.id(), turma.nome(), turma.disciplina(),
                            turma.horarioPadrao(), historico.aulasDadas(), historico.presencas(),
                            historico.percentual(), historico.minimo(), historico.abaixoDoMinimo(), aberta);
                }).toList();
    }
}
