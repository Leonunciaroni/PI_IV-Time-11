package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;

/** Servidor → cliente: conexão autenticada. */
public record AuthOk(String usuarioId, String nome, Papel papel, long expiraEm) {
}
