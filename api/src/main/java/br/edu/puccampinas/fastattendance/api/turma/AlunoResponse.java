/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.dominio.Usuario;

/** Dados do aluno exibidos ao professor; o e-mail não é exposto. */
public record AlunoResponse(String id, String nome, String matricula) {

    public static AlunoResponse de(Usuario aluno) {
        return new AlunoResponse(aluno.id(), aluno.nome(), aluno.matricula());
    }
}
