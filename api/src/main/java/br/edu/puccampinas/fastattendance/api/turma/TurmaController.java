package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Turmas do professor autenticado; o acesso por papel é definido em {@code SegurancaConfig}. */
@RestController
@RequestMapping("/api/turmas")
public class TurmaController {

    private final TurmaService turmas;
    private final ImportacaoService importacao;

    public TurmaController(TurmaService turmas, ImportacaoService importacao) {
        this.turmas = turmas;
        this.importacao = importacao;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TurmaResponse criar(@AuthenticationPrincipal UsuarioAutenticado professor,
                               @Valid @RequestBody TurmaRequest requisicao) {
        return turmas.criar(professor.id(), requisicao);
    }

    @GetMapping
    public List<TurmaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado professor) {
        return turmas.listar(professor.id());
    }

    @GetMapping("/{id}")
    public TurmaResponse detalhar(@AuthenticationPrincipal UsuarioAutenticado professor, @PathVariable String id) {
        return turmas.detalhar(professor.id(), id);
    }

    @GetMapping("/{id}/alunos")
    public List<AlunoResponse> alunos(@AuthenticationPrincipal UsuarioAutenticado professor,
                                      @PathVariable String id) {
        return turmas.alunos(professor.id(), id);
    }

    /** Alunos que ainda não estão na turma; {@code busca} filtra por nome ou matrícula. */
    @GetMapping("/{id}/candidatos")
    public List<AlunoResponse> candidatos(@AuthenticationPrincipal UsuarioAutenticado professor,
                                          @PathVariable String id,
                                          @RequestParam(required = false) String busca) {
        return turmas.candidatos(professor.id(), id, busca);
    }

    @PostMapping("/{id}/matriculas")
    public MatriculaResponse matricular(@AuthenticationPrincipal UsuarioAutenticado professor,
                                        @PathVariable String id,
                                        @Valid @RequestBody MatriculaRequest requisicao) {
        return turmas.matricular(professor.id(), id, requisicao);
    }

    @DeleteMapping("/{id}/matriculas/{alunoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerMatricula(@AuthenticationPrincipal UsuarioAutenticado professor,
                                 @PathVariable String id,
                                 @PathVariable String alunoId) {
        turmas.removerMatricula(professor.id(), id, alunoId);
    }

    /** Importa alunos de um CSV (campo {@code arquivo}) e devolve o relatório de linhas aceitas e rejeitadas. */
    @PostMapping(path = "/{id}/importacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoResponse importar(@AuthenticationPrincipal UsuarioAutenticado professor,
                                       @PathVariable String id,
                                       @RequestParam(required = false) MultipartFile arquivo) {
        return importacao.importar(professor.id(), id, arquivo);
    }
}
