package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.PresencaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.SessaoRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EstatisticaService {

    private final TurmaRepository turmas;
    private final MatriculaRepository matriculas;
    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final PresencaRepository presencas;

    public EstatisticaService(TurmaRepository turmas, MatriculaRepository matriculas, UsuarioRepository usuarios,
                              SessaoRepository sessoes, PresencaRepository presencas) {
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.presencas = presencas;
    }

    /** Estatísticas da turma do professor; só aulas já encerradas entram no cálculo. */
    public EstatisticasResponse estatisticas(String professorId, String turmaId) {
        // A turma de outro professor responde como inexistente, como nas demais rotas de turma
        Turma turma = turmas.findByIdAndProfessorId(turmaId, professorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada"));

        List<String> alunoIds = matriculas.findByTurmaId(turma.id()).stream().map(Matricula::alunoId).toList();
        List<Usuario> alunos = usuarios.findAllById(alunoIds);

        return CalculadoraDeFrequencia.calcular(
                sessoes.findByTurmaIdAndStatus(turma.id(), StatusSessao.ENCERRADA),
                alunos,
                presencas.findByTurmaId(turma.id()));
    }
}
