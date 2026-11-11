package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import java.util.List;

/** Servidor -> professor: estado atual da sessão (tipo sessao.estado). Resposta a PedidoDeAcompanharSessao. */
public class RespostaDeAcompanharSessao extends Comunicado
{
    private String sessaoId;
    private ModoChamada modo;
    private long abertaEm;
    private List<PresenteResumo> presentes;
    private int totalMatriculados;

    public RespostaDeAcompanharSessao (String sessaoId, ModoChamada modo, long abertaEm, List<PresenteResumo> presentes, int totalMatriculados)
    {
        this.sessaoId = sessaoId;
        this.modo = modo;
        this.abertaEm = abertaEm;
        this.presentes = presentes;
        this.totalMatriculados = totalMatriculados;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public ModoChamada getModo ()
    {
        return this.modo;
    }

    public long getAbertaEm ()
    {
        return this.abertaEm;
    }

    public List<PresenteResumo> getPresentes ()
    {
        return this.presentes;
    }

    public int getTotalMatriculados ()
    {
        return this.totalMatriculados;
    }

    public String toString ()
    {
        return "RespostaDeAcompanharSessao[" + "sessaoId=" + this.sessaoId + ", " + "modo=" + this.modo + ", " + "abertaEm=" + this.abertaEm + ", " + "presentes=" + this.presentes + ", " + "totalMatriculados=" + this.totalMatriculados + "]";
    }
}
