/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;

/** Servidor → cliente: falha ao processar a requisição indicada em {@code ref}. */
public record Erro(CodigoErro codigo, String mensagem) {
}
