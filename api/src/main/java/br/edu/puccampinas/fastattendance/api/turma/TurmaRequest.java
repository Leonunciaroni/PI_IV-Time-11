package br.edu.puccampinas.fastattendance.api.turma;

import jakarta.validation.constraints.NotBlank;

public record TurmaRequest(
        @NotBlank String nome,
        @NotBlank String disciplina,
        @NotBlank @HorarioValido String horarioPadrao) {
}
