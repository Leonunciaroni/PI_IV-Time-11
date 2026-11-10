package br.edu.puccampinas.fastattendance.common.comunicado;

/** Servidor -> aluno: presença registrada (tipo checkin.ok). Resposta a PedidoDeCheckin. */
public class RespostaDeCheckin extends Comunicado
{
    private String sessaoId;
    private long registradoEm;

    public RespostaDeCheckin (String sessaoId, long registradoEm)
    {
        this.sessaoId = sessaoId;
        this.registradoEm = registradoEm;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public long getRegistradoEm ()
    {
        return this.registradoEm;
    }

    public String toString ()
    {
        return "RespostaDeCheckin[" + "sessaoId=" + this.sessaoId + ", " + "registradoEm=" + this.registradoEm + "]";
    }
}
