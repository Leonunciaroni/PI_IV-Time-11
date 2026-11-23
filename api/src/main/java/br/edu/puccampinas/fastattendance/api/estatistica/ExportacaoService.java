/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
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

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Exporta a frequência da turma em CSV; a turma de outro professor responde como inexistente. */
@Service
public class ExportacaoService {

    /** Arquivo pronto para download. */
    public record ArquivoCsv(String nome, String conteudo) {
    }

    private final TurmaRepository turmas;
    private final MatriculaRepository matriculas;
    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final PresencaRepository presencas;

    public ExportacaoService(TurmaRepository turmas, MatriculaRepository matriculas, UsuarioRepository usuarios,
                             SessaoRepository sessoes, PresencaRepository presencas) {
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.presencas = presencas;
    }

    /** Frequência acumulada de cada aluno nas aulas já encerradas. */
    public ArquivoCsv consolidado(String professorId, String turmaId) {
        Turma turma = daProfessor(professorId, turmaId);
        String conteudo = GeradorDeCsvFrequencia.consolidado(
                sessoes.findByTurmaIdAndStatus(turma.id(), StatusSessao.ENCERRADA),
                alunosDaTurma(turma),
                presencas.findByTurmaId(turma.id()));
        return new ArquivoCsv("frequencia-" + apelido(turma) + ".csv", conteudo);
    }

    /** Chamada de uma aula da turma. */
    public ArquivoCsv chamada(String professorId, String turmaId, String sessaoId) {
        Turma turma = daProfessor(professorId, turmaId);
        Sessao aula = sessoes.findById(sessaoId)
                .filter(s -> s.turmaId().equals(turma.id()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aula não encontrada"));
        List<Presenca> daAula = presencas.findByTurmaId(turma.id()).stream()
                .filter(p -> p.sessaoId().equals(aula.id()))
                .toList();
        String conteudo = GeradorDeCsvFrequencia.chamada(aula, alunosDaTurma(turma), daAula);
        return new ArquivoCsv("chamada-" + apelido(turma) + "-" + GeradorDeCsvFrequencia.dataDaAula(aula) + ".csv",
                conteudo);
    }

    private Turma daProfessor(String professorId, String turmaId) {
        return turmas.findByIdAndProfessorId(turmaId, professorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada"));
    }

    private List<Usuario> alunosDaTurma(Turma turma) {
        List<String> alunoIds = matriculas.findByTurmaId(turma.id()).stream().map(Matricula::alunoId).toList();
        return usuarios.findAllById(alunoIds);
    }

    /** Nome da turma sem acentos nem símbolos, para compor o nome do arquivo. */
    private static String apelido(Turma turma) {
        String limpo = Normalizer.normalize(turma.nome(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return limpo.isEmpty() ? "turma" : limpo;
    }
}
