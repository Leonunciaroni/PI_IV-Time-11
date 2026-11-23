/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import java.util.ArrayDeque;

/**
 * Pedidos do navegador que aguardam resposta, na ordem em que foram enviados ao servidor de sessão.
 * <p>
 * O servidor responde exatamente uma vez a cada pedido, em ordem (docs/servidor-modelo-socket.md,
 * seção 2). Por isso a resposta que chega é atribuída à cabeça da fila. O {@code ref} pode ser
 * {@code null} quando o pedido não trouxe {@code id}; a fila guarda esse caso assim mesmo.
 */
final class PendentesDeResposta {

    /** Limite de pedidos sem resposta por conexão (docs/protocolo-v1.md, seção 1). */
    static final int MAXIMO = 64;

    /** Pedido aguardando resposta; {@code ref} é o {@code id} dele, ou {@code null}. */
    record Pendencia(String ref) {
    }

    private final ArrayDeque<Pendencia> fila = new ArrayDeque<>();

    /** Enfileira um pedido; devolve {@code false} se já há {@link #MAXIMO} pendentes. */
    synchronized boolean enfileirar(String ref) {
        if (fila.size() >= MAXIMO) {
            return false;
        }
        fila.addLast(new Pendencia(ref));
        return true;
    }

    /** Remove o pedido mais antigo; {@code null} se não há nenhum pendente (o servidor respondeu sem pedido). */
    synchronized Pendencia desenfileirar() {
        return fila.pollFirst();
    }

    synchronized int tamanho() {
        return fila.size();
    }
}
