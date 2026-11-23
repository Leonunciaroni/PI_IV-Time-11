/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cliente WebSocket que fala como os apps web: frames de texto JSON do protocolo v1. Usa o cliente
 * WebSocket do JDK, sem dependência extra.
 */
final class ClienteNavegador implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long PRAZO_MS = 3_000;

    private final BlockingQueue<String> recebidos = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> fechamento = new CompletableFuture<>();
    private final WebSocket ws;

    ClienteNavegador(int porta) throws Exception {
        ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + porta + "/ws"), new Ouvinte())
                .get(5, TimeUnit.SECONDS);
    }

    void enviar(String texto) throws Exception {
        ws.sendText(texto, true).get(5, TimeUnit.SECONDS);
    }

    void enviarBinario(byte[] bytes) throws Exception {
        ws.sendBinary(ByteBuffer.wrap(bytes), true).get(5, TimeUnit.SECONDS);
    }

    /** Próximo frame; falha se nada chegar em 3 s. */
    JsonNode proximo() {
        String texto;
        try {
            texto = recebidos.poll(PRAZO_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrompido", e);
        }
        if (texto == null) {
            fail("Nenhum frame em " + PRAZO_MS + " ms");
        }
        return JSON.readTree(texto);
    }

    /** Próximo frame, que precisa ser do tipo indicado. */
    JsonNode proximo(String tipo) {
        JsonNode frame = proximo();
        assertNotNull(frame.get("tipo"), frame.toString());
        assertEquals(tipo, frame.get("tipo").stringValue(), frame.toString());
        return frame;
    }

    /** Afirma que nenhum frame chega no prazo indicado. */
    void nenhumFrame(long ms) throws InterruptedException {
        String texto = recebidos.poll(ms, TimeUnit.MILLISECONDS);
        assertNull(texto, "Frame inesperado: " + texto);
    }

    /** Código de fechamento recebido, afirmando que a conexão fecha dentro do prazo. */
    int aguardarFechamento(long ms) throws Exception {
        try {
            return fechamento.get(ms, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("A conexão não fechou em " + ms + " ms", e);
        }
    }

    /** Afirma o código de fechamento recebido dentro de 5 s ({@code 1006} indica queda sem frame de fechamento). */
    void aguardarFechamentoCom(int codigoEsperado) throws Exception {
        int codigo = aguardarFechamento(5_000);
        assertEquals(codigoEsperado, codigo, "código de fechamento");
    }

    @Override
    public void close() {
        if (!fechamento.isDone()) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                // já fechado
            }
        }
    }

    private final class Ouvinte implements WebSocket.Listener {

        private final StringBuilder parcial = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(Long.MAX_VALUE);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            parcial.append(data);
            if (last) {
                recebidos.add(parcial.toString());
                parcial.setLength(0);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            fechamento.complete(statusCode);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            // Queda sem frame de fechamento: a conexão terminou; quem espera o código recebe 1006
            fechamento.complete(1006);
        }
    }
}
