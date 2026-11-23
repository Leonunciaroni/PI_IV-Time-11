/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

/** Servidor -> professor: resultado de PedidoDeMarcarPresencaEmLote (tipo presenca.lote_ok). */
public class RespostaDeMarcarPresencaEmLote extends Comunicado
{
    private String sessaoId;
    private int totalPresentes;
    private int totalMatriculados;

    public RespostaDeMarcarPresencaEmLote (String sessaoId, int totalPresentes, int totalMatriculados)
    {
        this.sessaoId = sessaoId;
        this.totalPresentes = totalPresentes;
        this.totalMatriculados = totalMatriculados;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public int getTotalPresentes ()
    {
        return this.totalPresentes;
    }

    public int getTotalMatriculados ()
    {
        return this.totalMatriculados;
    }

    public String toString ()
    {
        return "RespostaDeMarcarPresencaEmLote[" + "sessaoId=" + this.sessaoId + ", " + "totalPresentes=" + this.totalPresentes + ", " + "totalMatriculados=" + this.totalMatriculados + "]";
    }
}
