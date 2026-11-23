/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.common.comunicado;

/**
 * Marca os comunicados não solicitados que o servidor empurra aos acompanhantes de uma sessão.
 * <p>
 * Eventos não respondem a nenhum pedido, então não têm {@code ref} e não podem ocupar a vez de uma
 * resposta: a ponte casa as respostas com os pedidos pela ordem de chegada
 * ({@code PendentesDeResposta}). Por isso a ponte reconhece eventos por esta interface, e não por uma
 * lista de classes que precisaria ser lembrada a cada evento novo.
 */
public interface Evento
{}
