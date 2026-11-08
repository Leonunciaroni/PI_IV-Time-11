package br.edu.puccampinas.fastattendance.common.seguranca;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;

import java.time.Instant;

/** Identidade extraída de um JWT válido. */
public record UsuarioAutenticado(String id, String nome, Papel papel, Instant expiraEm) {
}
