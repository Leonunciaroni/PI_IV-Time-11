/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/** Servidor -> professor: modo atual da chamada (tipo sessao.modo_alterado, como resposta). Resposta a PedidoDeTrocarModo. Distinta de EventoDeModoAlterado. */
public class RespostaDeTrocarModo extends Comunicado
{
    private String sessaoId;
    private ModoChamada modo;
    private String salaId;
    private long alteradoEm;

    public RespostaDeTrocarModo (String sessaoId, ModoChamada modo, String salaId, long alteradoEm)
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
        return "RespostaDeTrocarModo[" + "sessaoId=" + this.sessaoId + ", " + "modo=" + this.modo + ", " + "salaId=" + this.salaId + ", " + "alteradoEm=" + this.alteradoEm + "]";
    }
}
