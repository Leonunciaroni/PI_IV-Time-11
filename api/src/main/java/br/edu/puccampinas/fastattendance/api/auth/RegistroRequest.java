/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.auth;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A senha é limitada a 72 caracteres, limite de entrada do BCrypt. */
public record RegistroRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @NotNull Papel papel,
        @NotBlank @Size(max = 30) String matricula) {
}
