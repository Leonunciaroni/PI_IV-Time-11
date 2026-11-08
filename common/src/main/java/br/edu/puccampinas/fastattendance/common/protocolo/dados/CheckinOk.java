package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Servidor → aluno: presença registrada. */
public record CheckinOk(String sessaoId, long registradoEm) {
}
