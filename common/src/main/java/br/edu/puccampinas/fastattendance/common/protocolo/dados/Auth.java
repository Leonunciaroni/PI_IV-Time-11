package br.edu.puccampinas.fastattendance.common.protocolo.dados;

/** Cliente → servidor: autentica a conexão com o JWT emitido pela API. */
public record Auth(String token) {
}
