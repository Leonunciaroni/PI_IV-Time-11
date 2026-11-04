package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;

/** Servidor -> cliente: falha ao processar um pedido. Sempre é a resposta do pedido que falhou (tipo erro). Também é a única resposta a pedido recusado pelo limite. */
public class Erro extends Comunicado
{
    private CodigoErro codigo;
    private String mensagem;

    public Erro (CodigoErro codigo, String mensagem)
    {
        this.codigo = codigo;
        this.mensagem = mensagem;
    }

    public CodigoErro getCodigo ()
    {
        return this.codigo;
    }

    public String getMensagem ()
    {
        return this.mensagem;
    }

    public String toString ()
    {
        return "Erro[" + "codigo=" + this.codigo + ", " + "mensagem=" + this.mensagem + "]";
    }
}
