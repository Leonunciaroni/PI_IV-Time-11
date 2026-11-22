package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.protocolo.dados.MotivoEncerramento;

/** Servidor -> professor acompanhando (tipo sessao.encerrada, sem ref): sessão encerrada. Não solicitado. */
public class EventoDeSessaoEncerrada extends Comunicado implements Evento
{
    private String sessaoId;
    private MotivoEncerramento motivo;
    private long encerradaEm;
    private int totalPresentes;

    public EventoDeSessaoEncerrada (String sessaoId, MotivoEncerramento motivo, long encerradaEm, int totalPresentes)
    {
        this.sessaoId = sessaoId;
        this.motivo = motivo;
        this.encerradaEm = encerradaEm;
        this.totalPresentes = totalPresentes;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public MotivoEncerramento getMotivo ()
    {
        return this.motivo;
    }

    public long getEncerradaEm ()
    {
        return this.encerradaEm;
    }

    public int getTotalPresentes ()
    {
        return this.totalPresentes;
    }

    public String toString ()
    {
        return "EventoDeSessaoEncerrada[" + "sessaoId=" + this.sessaoId + ", " + "motivo=" + this.motivo + ", " + "encerradaEm=" + this.encerradaEm + ", " + "totalPresentes=" + this.totalPresentes + "]";
    }
}
