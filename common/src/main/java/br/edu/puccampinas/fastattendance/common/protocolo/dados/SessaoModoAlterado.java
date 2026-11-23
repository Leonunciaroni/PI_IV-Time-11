/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/**
 * Servidor → professor: modo atual da chamada depois de {@code sessao.trocar_modo}. É a resposta ao
 * pedido e também o evento enviado aos professores que acompanham a sessão. {@code salaId} é
 * {@code null} quando a sessão não tem sala.
 */
public record SessaoModoAlterado(String sessaoId, ModoChamada modo, String salaId, long alteradoEm) {
}
