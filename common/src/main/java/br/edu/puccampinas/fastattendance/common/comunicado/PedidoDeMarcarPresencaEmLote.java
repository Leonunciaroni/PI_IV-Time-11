package br.edu.puccampinas.fastattendance.common.comunicado;

import java.util.List;

/** Professor -> servidor: marca todos com presente e o oposto nas exceções (tipo presenca.marcar_lote). Resposta: RespostaDeMarcarPresencaEmLote ou Erro. */
public class PedidoDeMarcarPresencaEmLote extends Comunicado
{
    private String sessaoId;
    private boolean presente;
    private List<String> excecoes;

    public PedidoDeMarcarPresencaEmLote (String sessaoId, boolean presente, List<String> excecoes)
    {
        this.sessaoId = sessaoId;
        this.presente = presente;
        this.excecoes = excecoes;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public boolean isPresente ()
    {
        return this.presente;
    }

    public List<String> getExcecoes ()
    {
        return this.excecoes;
    }

    public String toString ()
    {
        return "PedidoDeMarcarPresencaEmLote[" + "sessaoId=" + this.sessaoId + ", " + "presente=" + this.presente + ", " + "excecoes=" + this.excecoes + "]";
    }
}
