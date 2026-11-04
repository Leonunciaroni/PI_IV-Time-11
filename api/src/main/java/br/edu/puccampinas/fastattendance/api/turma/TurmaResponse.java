package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.dominio.Turma;

import java.time.Instant;

/** Turma com o número de alunos matriculados, usado nas telas de lista e de detalhes. */
public record TurmaResponse(String id, String nome, String disciplina, String horarioPadrao,
                            long totalAlunos, Instant criadaEm) {

    public static TurmaResponse de(Turma turma, long totalAlunos) {
        return new TurmaResponse(turma.id(), turma.nome(), turma.disciplina(), turma.horarioPadrao(),
                totalAlunos, turma.criadaEm());
    }
}
