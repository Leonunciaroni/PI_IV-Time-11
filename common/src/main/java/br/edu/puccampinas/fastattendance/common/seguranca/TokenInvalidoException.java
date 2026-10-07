package br.edu.puccampinas.fastattendance.common.seguranca;

/** JWT malformado, com assinatura incorreta, expirado ou com claims inválidas. */
public class TokenInvalidoException extends Exception {

    public TokenInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
