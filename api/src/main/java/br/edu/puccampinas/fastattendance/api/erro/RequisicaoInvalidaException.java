/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.api.erro;

/** Requisição bem formada, mas com conteúdo que as regras de negócio não aceitam. */
public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
