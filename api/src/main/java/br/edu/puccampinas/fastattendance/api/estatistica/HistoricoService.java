package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.erro.AcessoNegadoException;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.PresencaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.SessaoRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import org.springframework.stereotype.Service;

@Service
public class HistoricoService {

    private final TurmaRepository turmas;
    private final MatriculaRepository matriculas;
    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final PresencaRepository presencas;

    public HistoricoService(TurmaRepository turmas, MatriculaRepository matriculas, UsuarioRepository usuarios,
                            SessaoRepository sessoes, PresencaRepository presencas) {
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.presencas = presencas;
    }

    /**
     * Histórico de um aluno na turma. O professor vê alunos da própria turma; o aluno vê só o seu
     * próprio histórico.
     */
    public HistoricoResponse historico(UsuarioAutenticado solicitante, String turmaId, String alunoId) {
        Turma turma = turmaVisivel(solicitante, turmaId, alunoId);

        // Quem não está matriculado responde como inexistente na turma
        if (!matriculas.existsByTurmaIdAndAlunoId(turma.id(), alunoId)) {
            throw new RecursoNaoEncontradoException("Aluno não encontrado na turma");
        }
        Usuario aluno = usuarios.findById(alunoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno não encontrado na turma"));

        return CalculadoraDeHistorico.calcular(
                turma.id(),
                aluno,
                sessoes.findByTurmaIdAndStatus(turma.id(), StatusSessao.ENCERRADA),
                presencas.findByTurmaIdAndAlunoId(turma.id(), alunoId));
    }

    private Turma turmaVisivel(UsuarioAutenticado solicitante, String turmaId, String alunoId) {
        if (solicitante.papel() == Papel.ALUNO) {
            if (!solicitante.id().equals(alunoId)) {
                throw new AcessoNegadoException("Você só pode ver o seu próprio histórico.");
            }
            return turmas.findById(turmaId).orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada"));
        }
        // A turma de outro professor responde como inexistente, como nas demais rotas de turma
        return turmas.findByIdAndProfessorId(turmaId, solicitante.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada"));
    }
}
