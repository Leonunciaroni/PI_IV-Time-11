/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.dominio;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Vínculo de um aluno com uma turma. */
@Document(Colecoes.MATRICULAS)
public record Matricula(
        @Id String id,
        String turmaId,
        String alunoId,
        @CreatedDate Instant criadaEm) {
}
