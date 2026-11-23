// Guilherme Preventi Correia
// RA: 25001694

package br.edu.puccampinas.fastattendance.api.aluno;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/aluno")
public class InicioAlunoController {

    private final InicioAlunoService inicio;

    public InicioAlunoController(InicioAlunoService inicio) {
        this.inicio = inicio;
    }

    @GetMapping("/turmas")
    public List<TurmaDoAlunoResponse> turmas(@AuthenticationPrincipal UsuarioAutenticado aluno) {
        return inicio.listar(aluno.id());
    }
}
