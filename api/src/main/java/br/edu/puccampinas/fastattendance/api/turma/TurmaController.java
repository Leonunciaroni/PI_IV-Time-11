package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Turmas do professor autenticado; o acesso por papel é definido em {@code SegurancaConfig}. */
@RestController
@RequestMapping("/api/turmas")
public class TurmaController {

    private final TurmaService turmas;

    public TurmaController(TurmaService turmas) {
        this.turmas = turmas;
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
}
