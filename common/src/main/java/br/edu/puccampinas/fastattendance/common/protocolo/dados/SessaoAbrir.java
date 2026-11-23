/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/** Professor → servidor: abre a chamada de uma turma no modo escolhido. */
public record SessaoAbrir(String turmaId, ModoChamada modo, String salaId) {
}
