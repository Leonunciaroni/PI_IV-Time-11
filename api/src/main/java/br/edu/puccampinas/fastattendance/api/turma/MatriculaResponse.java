package br.edu.puccampinas.fastattendance.api.turma;

/**
 * Resultado de uma matrícula em lote.
 *
 * @param matriculados    alunos matriculados nesta chamada
 * @param jaMatriculados  alunos da lista que já estavam na turma (ignorados)
 * @param totalAlunos     total de alunos da turma depois da operação
 */
public record MatriculaResponse(int matriculados, int jaMatriculados, long totalAlunos) {
}
