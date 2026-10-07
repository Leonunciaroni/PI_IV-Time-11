package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;

/** Item da lista de presentes em {@link SessaoEstado}. */
public record PresenteResumo(
        String alunoId,
        String nome,
        OrigemPresenca origem,
        long registradoEm) {
}
