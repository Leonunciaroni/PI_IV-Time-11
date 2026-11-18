package br.edu.puccampinas.fastattendance.common.comunicado;

/** Professor -> servidor: marca um aluno como presente ou ausente, no modo clássico (tipo presenca.marcar). Resposta: RespostaDeMarcarPresenca ou Erro. */
public class PedidoDeMarcarPresenca extends Comunicado
{
    private String sessaoId;
    private String alunoId;
    private boolean presente;

    public PedidoDeMarcarPresenca (String sessaoId, String alunoId, boolean presente)
    {
        this.sessaoId = sessaoId;
        this.alunoId = alunoId;
        this.presente = presente;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public String getAlunoId ()
    {
        return this.alunoId;
    }

    public boolean isPresente ()
    {
        return this.presente;
    }

    public String toString ()
    {
        return "PedidoDeMarcarPresenca[" + "sessaoId=" + this.sessaoId + ", " + "alunoId=" + this.alunoId + ", " + "presente=" + this.presente + "]";
    }
}
