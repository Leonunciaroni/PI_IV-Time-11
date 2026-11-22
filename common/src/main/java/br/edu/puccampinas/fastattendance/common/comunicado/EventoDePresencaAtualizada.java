package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;

/** Servidor -> professor acompanhando (tipo presenca.atualizada, sem ref): presença gravada por qualquer réplica. Não solicitado. */
public class EventoDePresencaAtualizada extends Comunicado implements Evento
{
    private String sessaoId;
    private String alunoId;
    private String nomeAluno;
    private boolean presente;
    private OrigemPresenca origem;
    private long registradoEm;
    private int totalPresentes;
    private int totalMatriculados;

    public EventoDePresencaAtualizada (String sessaoId, String alunoId, String nomeAluno, boolean presente, OrigemPresenca origem, long registradoEm, int totalPresentes, int totalMatriculados)
    {
        this.sessaoId = sessaoId;
        this.alunoId = alunoId;
        this.nomeAluno = nomeAluno;
        this.presente = presente;
        this.origem = origem;
        this.registradoEm = registradoEm;
        this.totalPresentes = totalPresentes;
        this.totalMatriculados = totalMatriculados;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public String getAlunoId ()
    {
        return this.alunoId;
    }

    public String getNomeAluno ()
    {
        return this.nomeAluno;
    }

    public boolean isPresente ()
    {
        return this.presente;
    }

    public OrigemPresenca getOrigem ()
    {
        return this.origem;
    }

    public long getRegistradoEm ()
    {
        return this.registradoEm;
    }

    public int getTotalPresentes ()
    {
        return this.totalPresentes;
    }

    public int getTotalMatriculados ()
    {
        return this.totalMatriculados;
    }

    public String toString ()
    {
        return "EventoDePresencaAtualizada[" + "sessaoId=" + this.sessaoId + ", " + "alunoId=" + this.alunoId + ", " + "nomeAluno=" + this.nomeAluno + ", " + "presente=" + this.presente + ", " + "origem=" + this.origem + ", " + "registradoEm=" + this.registradoEm + ", " + "totalPresentes=" + this.totalPresentes + ", " + "totalMatriculados=" + this.totalMatriculados + "]";
    }
}
