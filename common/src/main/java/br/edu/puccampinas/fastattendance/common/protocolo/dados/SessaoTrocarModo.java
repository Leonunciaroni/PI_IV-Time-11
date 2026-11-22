package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/**
 * Professor → servidor: troca o modo de uma chamada aberta, mantendo as presenças. {@code salaId} é
 * obrigatório para ir ao modo {@code QRCODE} quando a sessão ainda não tem sala; nos demais casos,
 * pode ser {@code null}.
 */
public record SessaoTrocarModo(String sessaoId, ModoChamada modo, String salaId) {
}
