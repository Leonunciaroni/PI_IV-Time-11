/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Professor → servidor: referência a uma sessão ({@code sessao.encerrar}, {@code sessao.acompanhar}). */
public record SessaoRef(String sessaoId) {
}
