/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

/** Cliente -> servidor: autentica a conexão com o JWT emitido pela API (tipo auth). Resposta: RespostaDeAutenticacao ou Erro. */
public class PedidoDeAutenticacao extends Comunicado
{
    private String token;

    public PedidoDeAutenticacao (String token)
    {
        this.token = token;
    }

    public String getToken ()
    {
        return this.token;
    }

    public String toString ()
    {
        return "PedidoDeAutenticacao[" + "token=" + "***" + "]";
    }
}
