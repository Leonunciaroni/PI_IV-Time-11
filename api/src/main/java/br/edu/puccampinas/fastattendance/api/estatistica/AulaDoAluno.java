/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

import java.time.Instant;

/** Uma aula da turma e se o aluno esteve presente nela. */
public record AulaDoAluno(String sessaoId, Instant data, ModoChamada modo, boolean presente) {
}
