/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/** Professor -> servidor: troca o modo da chamada aberta (tipo sessao.trocar_modo). Resposta: RespostaDeTrocarModo ou Erro. */
public class PedidoDeTrocarModo extends Comunicado
{
    private String sessaoId;
    private ModoChamada modo;
    private String salaId;

    public PedidoDeTrocarModo (String sessaoId, ModoChamada modo, String salaId)
    {
        this.sessaoId = sessaoId;
        this.modo = modo;
        this.salaId = salaId;
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

    public String toString ()
    {
        return "PedidoDeTrocarModo[" + "sessaoId=" + this.sessaoId + ", " + "modo=" + this.modo + ", " + "salaId=" + this.salaId + "]";
    }
}
