package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/** Servidor -> professor: sessão aberta (tipo sessao.aberta). Resposta a PedidoDeAbrirSessao. Única resposta que leva o segredo TOTP. */
public class RespostaDeAbrirSessao extends Comunicado
{
    private String sessaoId;
    private String turmaId;
    private ModoChamada modo;
    private long abertaEm;
    private ConfiguracaoTotp totp;

    public RespostaDeAbrirSessao (String sessaoId, String turmaId, ModoChamada modo, long abertaEm, ConfiguracaoTotp totp)
    {
        this.sessaoId = sessaoId;
        this.turmaId = turmaId;
        this.modo = modo;
        this.abertaEm = abertaEm;
        this.totp = totp;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public String getTurmaId ()
    {
        return this.turmaId;
    }

    public ModoChamada getModo ()
    {
        return this.modo;
    }

    public long getAbertaEm ()
    {
        return this.abertaEm;
    }

    public ConfiguracaoTotp getTotp ()
    {
        return this.totp;
    }

    public String toString ()
    {
        return "RespostaDeAbrirSessao[" + "sessaoId=" + this.sessaoId + ", " + "turmaId=" + this.turmaId + ", " + "modo=" + this.modo + ", " + "abertaEm=" + this.abertaEm + ", " + "totp=" + this.totp + "]";
    }
}
