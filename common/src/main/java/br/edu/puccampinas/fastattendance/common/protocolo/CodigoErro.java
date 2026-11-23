/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo;

/** Códigos enviados em mensagens {@code erro} (seção 7 do protocolo). */
public enum CodigoErro {
    MENSAGEM_INVALIDA,
    VERSAO_NAO_SUPORTADA,
    AUTH_REQUERIDA,
    AUTH_INVALIDA,
    PERMISSAO_NEGADA,
    SESSAO_NAO_ENCONTRADA,
    SESSAO_ENCERRADA,
    SESSAO_JA_ABERTA,
    MODO_INCOMPATIVEL,
    TOKEN_INVALIDO,
    TOKEN_EXPIRADO,
    FORA_DO_RAIO,
    ALUNO_NAO_MATRICULADO,
    PRESENCA_DUPLICADA,
    DISPOSITIVO_JA_UTILIZADO,
    LIMITE_EXCEDIDO,
    ERRO_INTERNO
}
