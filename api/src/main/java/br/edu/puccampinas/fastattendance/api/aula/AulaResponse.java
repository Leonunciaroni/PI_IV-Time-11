// Guilherme Preventi Correia
// RA: 25001694

package br.edu.puccampinas.fastattendance.api.aula;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;

import java.time.Instant;

public record AulaResponse(String id, String turmaId, ModoChamada modo, StatusSessao status,
                           Instant abertaEm, Instant encerradaEm, long totalPresentes) {
}
