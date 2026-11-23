/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

import java.time.Instant;

/** Frequência de uma aula (sessão encerrada) da turma. */
public record AulaEstatistica(String sessaoId, Instant data, ModoChamada modo,
                              long presentes, long matriculados, double percentual) {
}
