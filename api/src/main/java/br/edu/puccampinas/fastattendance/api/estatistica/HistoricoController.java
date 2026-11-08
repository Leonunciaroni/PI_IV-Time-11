package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Histórico de frequência de um aluno; professor e aluno podem consultar, com regras no serviço. */
@RestController
@RequestMapping("/api/turmas/{id}/frequencia")
public class HistoricoController {

    private final HistoricoService historico;

    public HistoricoController(HistoricoService historico) {
        this.historico = historico;
    }

    @GetMapping("/{alunoId}")
    public HistoricoResponse historico(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                       @PathVariable String id, @PathVariable String alunoId) {
        return historico.historico(usuario, id, alunoId);
    }
}
