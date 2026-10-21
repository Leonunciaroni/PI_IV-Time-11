package br.edu.puccampinas.fastattendance.api.turma;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MatriculaRequest(
        @NotEmpty @Size(max = 200) List<@NotBlank String> alunoIds) {
}
