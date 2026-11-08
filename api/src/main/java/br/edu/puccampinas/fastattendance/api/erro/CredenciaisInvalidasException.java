package br.edu.puccampinas.fastattendance.api.erro;

/** E-mail inexistente ou senha incorreta; a mensagem é a mesma nos dois casos. */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha incorretos.");
    }
}
