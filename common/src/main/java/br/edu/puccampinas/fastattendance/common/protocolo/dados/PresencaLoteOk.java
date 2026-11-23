/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Servidor → professor: resultado de {@code presenca.marcar_lote}. */
public record PresencaLoteOk(String sessaoId, int totalPresentes, int totalMatriculados) {
}
