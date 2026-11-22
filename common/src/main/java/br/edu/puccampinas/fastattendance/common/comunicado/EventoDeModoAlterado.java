package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/** Servidor -> acompanhantes: o modo da chamada mudou (tipo sessao.modo_alterado, sem ref). Push não solicitado. */
public class EventoDeModoAlterado extends Comunicado implements Evento
{
    private String sessaoId;
    private ModoChamada modo;
    private String salaId;
    private long alteradoEm;

    public EventoDeModoAlterado (String sessaoId, ModoChamada modo, String salaId, long alteradoEm)
    {
        this.sessaoId = sessaoId;
        this.modo = modo;
        this.salaId = salaId;
        this.alteradoEm = alteradoEm;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public ModoChamada getModo ()
    {
        return this.modo;
    }

    public String getSalaId ()
    {
        return this.salaId;
    }

    public long getAlteradoEm ()
    {
        return this.alteradoEm;
    }

    public String toString ()
    {
        return "EventoDeModoAlterado[" + "sessaoId=" + this.sessaoId + ", " + "modo=" + this.modo + ", " + "salaId=" + this.salaId + ", " + "alteradoEm=" + this.alteradoEm + "]";
    }
}
