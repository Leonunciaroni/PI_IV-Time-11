/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.auth;

import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;

/** Dados públicos do usuário; nunca inclui o hash da senha. */
public record UsuarioResponse(String id, String nome, String email, Papel papel, String matricula) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.id(), usuario.nome(), usuario.email(), usuario.papel(),
                usuario.matricula());
    }
}
