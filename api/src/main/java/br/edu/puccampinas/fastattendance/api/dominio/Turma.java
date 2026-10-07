package br.edu.puccampinas.fastattendance.api.dominio;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Turma de uma disciplina, sob responsabilidade de um professor. */
@Document(Colecoes.TURMAS)
public record Turma(
        @Id String id,
        String nome,
        String disciplina,
        String horarioPadrao,
        String professorId,
        @CreatedDate Instant criadaEm) {
}
