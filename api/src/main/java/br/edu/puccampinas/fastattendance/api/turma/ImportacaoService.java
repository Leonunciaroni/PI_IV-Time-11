package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.erro.RequisicaoInvalidaException;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.api.turma.ImportacaoResponse.LinhaRejeitada;
import br.edu.puccampinas.fastattendance.api.turma.LeitorCsvAlunos.LinhaAluno;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Matricula na turma os alunos listados em um CSV. Só entram alunos que já têm conta: a conta é
 * encontrada pelo e-mail e a matrícula do arquivo precisa bater com a cadastrada.
 */
@Service
public class ImportacaoService {

    private final TurmaRepository turmas;
    private final MatriculaRepository matriculas;
    private final UsuarioRepository usuarios;
    private final LeitorCsvAlunos leitor = new LeitorCsvAlunos();

    public ImportacaoService(TurmaRepository turmas, MatriculaRepository matriculas, UsuarioRepository usuarios) {
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.usuarios = usuarios;
    }

    public ImportacaoResponse importar(String professorId, String turmaId, MultipartFile arquivo) {
        Turma turma = turmas.findByIdAndProfessorId(turmaId, professorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada"));
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RequisicaoInvalidaException("Envie um arquivo CSV.");
        }

        List<LinhaAluno> linhas;
        try (InputStream entrada = arquivo.getInputStream()) {
            linhas = leitor.ler(entrada);
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Não foi possível ler o arquivo.");
        }

        List<LinhaRejeitada> rejeitados = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        int importados = 0;
        int jaMatriculados = 0;

        for (LinhaAluno linha : linhas) {
            Optional<String> erro = validar(linha);
            if (erro.isPresent()) {
                rejeitados.add(new LinhaRejeitada(linha.numero(), erro.get()));
                continue;
            }
            String email = linha.email().toLowerCase(Locale.ROOT);
            if (!vistos.add(email)) {
                rejeitados.add(new LinhaRejeitada(linha.numero(), "E-mail repetido no arquivo."));
                continue;
            }
            Optional<Usuario> aluno = usuarios.findByEmail(email).filter(u -> u.papel() == Papel.ALUNO);
            if (aluno.isEmpty()) {
                rejeitados.add(new LinhaRejeitada(linha.numero(), "Aluno não cadastrado."));
                continue;
            }
            if (!linha.matricula().equals(aluno.get().matricula())) {
                rejeitados.add(new LinhaRejeitada(linha.numero(), "Matrícula não confere com o cadastro."));
                continue;
            }
            try {
                matriculas.insert(new Matricula(null, turma.id(), aluno.get().id(), null));
                importados++;
            } catch (DuplicateKeyException e) {
                // Já matriculado: o índice único turma_aluno_unico garante a idempotência
                jaMatriculados++;
            }
        }
        return new ImportacaoResponse(importados, jaMatriculados, rejeitados, matriculas.countByTurmaId(turma.id()));
    }

    private static Optional<String> validar(LinhaAluno linha) {
        if (linha.nome().isEmpty() || linha.email().isEmpty() || linha.matricula().isEmpty()) {
            return Optional.of("Nome, e-mail e matrícula são obrigatórios.");
        }
        if (!linha.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return Optional.of("E-mail inválido.");
        }
        return Optional.empty();
    }
}
