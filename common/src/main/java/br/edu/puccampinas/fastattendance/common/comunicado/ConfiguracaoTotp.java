/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

import java.io.Serializable;

/** Valor (não é comunicado): parâmetros TOTP (RFC 6238) da sessão, enviados só em RespostaDeAbrirSessao. */
public class ConfiguracaoTotp implements Serializable
{
    private String segredo;
    private String algoritmo;
    private int digitos;
    private int periodoSegundos;
    private long t0;

    public ConfiguracaoTotp (String segredo, String algoritmo, int digitos, int periodoSegundos, long t0)
    {
        this.segredo = segredo;
        this.algoritmo = algoritmo;
        this.digitos = digitos;
        this.periodoSegundos = periodoSegundos;
        this.t0 = t0;
    }

    public String getSegredo ()
    {
        return this.segredo;
    }

    public String getAlgoritmo ()
    {
        return this.algoritmo;
    }

    public int getDigitos ()
    {
        return this.digitos;
    }

    public int getPeriodoSegundos ()
    {
        return this.periodoSegundos;
    }

    public long getT0 ()
    {
        return this.t0;
    }

    public String toString ()
    {
        return "ConfiguracaoTotp[" + "segredo=" + "***" + ", " + "algoritmo=" + this.algoritmo + ", " + "digitos=" + this.digitos + ", " + "periodoSegundos=" + this.periodoSegundos + ", " + "t0=" + this.t0 + "]";
    }
}
