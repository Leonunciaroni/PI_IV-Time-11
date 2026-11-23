/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Servidor → aluno: presença registrada. */
public record CheckinOk(String sessaoId, long registradoEm) {
}
