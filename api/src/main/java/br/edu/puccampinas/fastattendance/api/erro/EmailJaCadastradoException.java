package br.edu.puccampinas.fastattendance.api.erro;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException(String email) {
        super("Já existe uma conta com o e-mail " + email + ".");
    }
}
