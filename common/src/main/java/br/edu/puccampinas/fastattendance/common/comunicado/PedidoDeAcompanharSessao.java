package br.edu.puccampinas.fastattendance.common.comunicado;

/** Professor -> servidor: passa a receber os eventos da sessão (tipo sessao.acompanhar). Resposta: RespostaDeAcompanharSessao ou Erro. */
public class PedidoDeAcompanharSessao extends Comunicado
{
    private String sessaoId;

    public PedidoDeAcompanharSessao (String sessaoId)
    {
        this.sessaoId = sessaoId;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public String toString ()
    {
        return "PedidoDeAcompanharSessao[" + "sessaoId=" + this.sessaoId + "]";
    }
}
