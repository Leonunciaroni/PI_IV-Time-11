/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

import java.util.List;

/** Servidor → professor: estado atual da sessão ao começar a acompanhá-la. */
public record SessaoEstado(
        String sessaoId,
        ModoChamada modo,
        long abertaEm,
        List<PresenteResumo> presentes,
        int totalMatriculados) {
}
