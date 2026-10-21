package br.edu.puccampinas.fastattendance.api.estatistica;

/** Frequência acumulada de um aluno em todas as aulas dadas da turma. */
public record AlunoEstatistica(String alunoId, String nome, String matricula,
                               long presencas, long aulas, double percentual) {
}
