/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.dominio;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Professor ou aluno.
 *
 * @param matricula RA do aluno ou registro funcional do professor
 * @param senhaHash hash BCrypt; a senha em texto nunca é persistida
 */
@Document(Colecoes.USUARIOS)
public record Usuario(
        @Id String id,
        String nome,
        String email,
        String senhaHash,
        Papel papel,
        String matricula,
        @CreatedDate Instant criadoEm) {
}
