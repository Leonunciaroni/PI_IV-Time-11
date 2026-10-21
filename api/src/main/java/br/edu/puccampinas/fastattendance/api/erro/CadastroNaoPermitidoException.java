package br.edu.puccampinas.fastattendance.api.erro;

public class CadastroNaoPermitidoException extends RuntimeException {

    public CadastroNaoPermitidoException() {
        super("Este e-mail não está autorizado a criar conta de professor.");
    }
}
