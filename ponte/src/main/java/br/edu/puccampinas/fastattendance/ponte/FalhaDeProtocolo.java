package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;

/**
 * Frame do navegador recusado pela ponte. A mensagem vira um {@code erro} com o {@code codigo} indicado;
 * o {@code ref} é o {@code id} do frame, ou {@code null} quando ele não pode ser lido.
 *
 * @param fechamento código de fechamento a aplicar depois do erro, ou {@code 0} para manter a conexão
 */
final class FalhaDeProtocolo extends RuntimeException {

    private final CodigoErro codigo;
    private final String ref;
    private final int fechamento;

    FalhaDeProtocolo(CodigoErro codigo, String mensagem, String ref, int fechamento) {
        super(mensagem);
        this.codigo = codigo;
        this.ref = ref;
        this.fechamento = fechamento;
    }

    FalhaDeProtocolo(CodigoErro codigo, String mensagem, String ref) {
        this(codigo, mensagem, ref, 0);
    }

    CodigoErro codigo() {
        return codigo;
    }

    String ref() {
        return ref;
    }

    int fechamento() {
        return fechamento;
    }
}
