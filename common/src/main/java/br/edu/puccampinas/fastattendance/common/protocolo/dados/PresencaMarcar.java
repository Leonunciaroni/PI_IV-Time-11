/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Professor → servidor: marca um aluno como presente ou ausente (modo clássico). */
public record PresencaMarcar(String sessaoId, String alunoId, boolean presente) {
}
