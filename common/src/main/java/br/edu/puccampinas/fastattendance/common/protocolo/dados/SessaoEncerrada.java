package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Servidor → clientes: sessão encerrada pelo professor ou por tempo limite. */
public record SessaoEncerrada(
        String sessaoId,
        MotivoEncerramento motivo,
        long encerradaEm,
        int totalPresentes) {
}
