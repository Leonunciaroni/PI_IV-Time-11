/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard de frequência da turma; o acesso só para professor é definido em {@code SegurancaConfig}. */
@RestController
@RequestMapping("/api/turmas/{id}/estatisticas")
public class EstatisticaController {

    private final EstatisticaService estatisticas;

    public EstatisticaController(EstatisticaService estatisticas) {
        this.estatisticas = estatisticas;
    }

    @GetMapping
    public EstatisticasResponse estatisticas(@AuthenticationPrincipal UsuarioAutenticado professor,
                                             @PathVariable String id) {
        return estatisticas.estatisticas(professor.id(), id);
    }
}
