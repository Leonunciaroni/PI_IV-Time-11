/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

/** Professor -> servidor: encerra a sessão (tipo sessao.encerrar). Resposta: RespostaDeEncerrarSessao ou Erro. */
public class PedidoDeEncerrarSessao extends Comunicado
{
    private String sessaoId;

    public PedidoDeEncerrarSessao (String sessaoId)
    {
        this.sessaoId = sessaoId;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public String toString ()
    {
        return "PedidoDeEncerrarSessao[" + "sessaoId=" + this.sessaoId + "]";
    }
}
