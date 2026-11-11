package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/** Professor -> servidor: abre a chamada de uma turma (tipo sessao.abrir). Resposta: RespostaDeAbrirSessao ou Erro. */
public class PedidoDeAbrirSessao extends Comunicado
{
    private String turmaId;
    private ModoChamada modo;
    private String salaId;

    public PedidoDeAbrirSessao (String turmaId, ModoChamada modo, String salaId)
    {
        this.turmaId = turmaId;
        this.modo = modo;
        this.salaId = salaId;
    }

    public String getTurmaId ()
    {
        return this.turmaId;
    }

    public ModoChamada getModo ()
    {
        return this.modo;
    }

    public String getSalaId ()
    {
        return this.salaId;
    }

    public String toString ()
    {
        return "PedidoDeAbrirSessao[" + "turmaId=" + this.turmaId + ", " + "modo=" + this.modo + ", " + "salaId=" + this.salaId + "]";
    }
}
