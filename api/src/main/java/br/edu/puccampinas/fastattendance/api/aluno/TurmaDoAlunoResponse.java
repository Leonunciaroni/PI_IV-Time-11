// Guilherme Preventi Correia
// RA: 25001694

package br.edu.puccampinas.fastattendance.api.aluno;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

import java.time.Instant;

/** Resumo para A2; nunca expõe o segredo TOTP da chamada. */
public record TurmaDoAlunoResponse(String id, String nome, String disciplina, String horarioPadrao,
                                   int aulasDadas, long presencas, double percentual, int minimo,
                                   boolean abaixoDoMinimo, ChamadaAberta chamadaAberta) {

    public record ChamadaAberta(String id, ModoChamada modo, Instant abertaEm) {
    }
}
