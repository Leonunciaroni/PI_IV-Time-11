// Guilherme Preventi Correia
// RA: 25001694

package br.edu.puccampinas.fastattendance.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** PATCH: campos ausentes são preservados; papel e matrícula não são editáveis. */
public record AtualizarPerfilRequest(
        @Size(max = 120, message = "O nome deve ter até 120 caracteres.") String nome,
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter até 254 caracteres.") String email,
        @Size(max = 72, message = "A senha atual deve ter até 72 caracteres.") String senhaAtual,
        @Size(min = 8, max = 72, message = "A nova senha deve ter de 8 a 72 caracteres.") String novaSenha) {
}
