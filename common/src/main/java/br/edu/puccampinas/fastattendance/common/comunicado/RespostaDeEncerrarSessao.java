package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.protocolo.dados.MotivoEncerramento;

/** Servidor -> professor: sessão encerrada (tipo sessao.encerrada, como resposta). Resposta a PedidoDeEncerrarSessao. Distinta de EventoDeSessaoEncerrada. */
public class RespostaDeEncerrarSessao extends Comunicado
{
    private String sessaoId;
    private MotivoEncerramento motivo;
    private long encerradaEm;
    private int totalPresentes;

    public RespostaDeEncerrarSessao (String sessaoId, MotivoEncerramento motivo, long encerradaEm, int totalPresentes)
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
        return "RespostaDeEncerrarSessao[" + "sessaoId=" + this.sessaoId + ", " + "motivo=" + this.motivo + ", " + "encerradaEm=" + this.encerradaEm + ", " + "totalPresentes=" + this.totalPresentes + "]";
    }
}
