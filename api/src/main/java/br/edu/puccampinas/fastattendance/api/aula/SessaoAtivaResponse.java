package br.edu.puccampinas.fastattendance.api.aula;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

import java.time.Instant;

/** Visão do aluno: não inclui segredo TOTP nem dados de outros alunos. */
public record SessaoAtivaResponse(String sessaoId, String turmaId, String turma, String disciplina,
                                  ModoChamada modo, Instant abertaEm) {
}
