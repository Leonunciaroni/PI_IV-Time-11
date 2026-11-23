/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;

/** Servidor -> cliente: conexão autenticada (tipo auth.ok). Resposta a PedidoDeAutenticacao. */
public class RespostaDeAutenticacao extends Comunicado
{
    private String usuarioId;
    private String nome;
    private Papel papel;
    private long expiraEm;

    public RespostaDeAutenticacao (String usuarioId, String nome, Papel papel, long expiraEm)
    {
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.papel = papel;
        this.expiraEm = expiraEm;
    }

    public String getUsuarioId ()
    {
        return this.usuarioId;
    }

    public String getNome ()
    {
        return this.nome;
    }

    public Papel getPapel ()
    {
        return this.papel;
    }

    public long getExpiraEm ()
    {
        return this.expiraEm;
    }

    public String toString ()
    {
        return "RespostaDeAutenticacao[" + "usuarioId=" + this.usuarioId + ", " + "nome=" + this.nome + ", " + "papel=" + this.papel + ", " + "expiraEm=" + this.expiraEm + "]";
    }
}
