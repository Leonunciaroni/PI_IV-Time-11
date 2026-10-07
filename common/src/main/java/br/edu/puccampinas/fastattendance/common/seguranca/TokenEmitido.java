package br.edu.puccampinas.fastattendance.common.seguranca;

import java.time.Instant;

/** JWT recém-emitido e o instante em que expira. */
public record TokenEmitido(String token, Instant expiraEm) {
}
