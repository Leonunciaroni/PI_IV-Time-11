package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import java.io.Serializable;

/** Valor (não é comunicado): item da lista de presentes de RespostaDeAcompanharSessao. */
public class PresenteResumo implements Serializable
{
    private String alunoId;
    private String nome;
    private OrigemPresenca origem;
    private long registradoEm;

    public PresenteResumo (String alunoId, String nome, OrigemPresenca origem, long registradoEm)
    {
        this.alunoId = alunoId;
        this.nome = nome;
        this.origem = origem;
        this.registradoEm = registradoEm;
    }

    public String getAlunoId ()
    {
        return this.alunoId;
    }

    public String getNome ()
    {
        return this.nome;
    }

    public OrigemPresenca getOrigem ()
    {
        return this.origem;
    }

    public long getRegistradoEm ()
    {
        return this.registradoEm;
    }

    public String toString ()
    {
        return "PresenteResumo[" + "alunoId=" + this.alunoId + ", " + "nome=" + this.nome + ", " + "origem=" + this.origem + ", " + "registradoEm=" + this.registradoEm + "]";
    }
}
