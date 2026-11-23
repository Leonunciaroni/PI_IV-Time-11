/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.erro;

/** O usuário está autenticado, mas não pode acessar o dado pedido. */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
