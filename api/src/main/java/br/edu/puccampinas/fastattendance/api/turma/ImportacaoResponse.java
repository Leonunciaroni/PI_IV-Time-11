/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.turma;

import java.util.List;

/**
 * Resultado da importação de alunos por CSV.
 *
 * @param importados      alunos matriculados nesta chamada
 * @param jaMatriculados  alunos do arquivo que já estavam na turma (ignorados)
 * @param rejeitados      linhas recusadas, com o motivo
 * @param totalAlunos     total de alunos da turma depois da operação
 */
public record ImportacaoResponse(int importados, int jaMatriculados, List<LinhaRejeitada> rejeitados,
                                 long totalAlunos) {

    /** Linha recusada; {@code linha} conta o cabeçalho como 1, como numera uma planilha. */
    public record LinhaRejeitada(int linha, String motivo) {
    }
}
