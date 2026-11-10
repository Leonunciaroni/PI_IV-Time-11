package br.edu.puccampinas.fastattendance.api.aula;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AulaController {
    private final AulaService aulas;

    public AulaController(AulaService aulas) { this.aulas = aulas; }

    @GetMapping("/api/turmas/{id}/aulas")
    public List<AulaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado professor, @PathVariable String id) {
        return aulas.aulas(professor.id(), id);
    }

    @GetMapping("/api/sessoes/ativas")
    public List<SessaoAtivaResponse> ativas(@AuthenticationPrincipal UsuarioAutenticado aluno) {
        return aulas.ativas(aluno.id());
    }
}
