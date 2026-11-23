/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Parâmetros TOTP (RFC 6238) enviados somente ao professor dono da sessão. */
public record ConfiguracaoTotp(
        String segredo,
        String algoritmo,
        int digitos,
        int periodoSegundos,
        long t0) {
}
