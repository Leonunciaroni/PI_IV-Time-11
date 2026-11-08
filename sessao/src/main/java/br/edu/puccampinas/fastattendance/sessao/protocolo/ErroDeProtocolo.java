package br.edu.puccampinas.fastattendance.sessao.protocolo;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;

/** Falha prevista no protocolo; o roteador a responde como mensagem {@code erro}. */
public class ErroDeProtocolo extends RuntimeException {

    private final CodigoErro codigo;

    public ErroDeProtocolo(CodigoErro codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public CodigoErro codigo() {
        return codigo;
    }
}
