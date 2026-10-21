package br.edu.puccampinas.fastattendance.api.turma;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TurmaRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 120) String disciplina,
        @NotBlank @Size(max = 60) String horarioPadrao) {
}
