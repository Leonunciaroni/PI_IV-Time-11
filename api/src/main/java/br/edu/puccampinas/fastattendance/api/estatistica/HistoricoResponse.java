/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

import java.util.List;

/**
 * Histórico de frequência de um aluno em uma turma.
 *
 * @param aulas em ordem cronológica, todas as aulas encerradas da turma
 */
public record HistoricoResponse(String turmaId, String alunoId, String nome, String matricula,
                                int aulasDadas, long presencas, double percentual, int minimo,
                                boolean abaixoDoMinimo, List<AulaDoAluno> aulas) {
}
