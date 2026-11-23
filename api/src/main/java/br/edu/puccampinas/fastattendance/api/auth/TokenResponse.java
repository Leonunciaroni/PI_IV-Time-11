/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.auth;

import java.time.Instant;

/** Resposta do login: o JWT e os dados do usuário autenticado. */
public record TokenResponse(String token, String tipo, Instant expiraEm, UsuarioResponse usuario) {

    public TokenResponse(String token, Instant expiraEm, UsuarioResponse usuario) {
        this(token, "Bearer", expiraEm, usuario);
    }
}
