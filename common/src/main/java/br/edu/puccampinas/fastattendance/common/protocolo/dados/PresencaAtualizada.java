package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;

/** Servidor → professor (evento): presença registrada ou alterada. */
public record PresencaAtualizada(
        String sessaoId,
        String alunoId,
        String nomeAluno,
        boolean presente,
        OrigemPresenca origem,
        long registradoEm,
        int totalPresentes,
        int totalMatriculados) {
}
