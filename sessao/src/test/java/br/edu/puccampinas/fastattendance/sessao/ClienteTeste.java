package br.edu.puccampinas.fastattendance.sessao;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Cliente WebSocket do JDK que acumula as mensagens recebidas para as asserções. */
final class ClienteTeste implements WebSocket.Listener, AutoCloseable {

    private final BlockingQueue<String> recebidas = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> fechamento = new CompletableFuture<>();
    private final StringBuilder parcial = new StringBuilder();
    private final WebSocket socket;

    ClienteTeste(int porta) {
        this.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + porta), this)
                .join();
    }

    void enviar(String texto) {
        socket.sendText(texto, true).join();
    }

    /** Próxima mensagem recebida, ou falha se nada chegar em 3 s. */
    String proxima() throws InterruptedException {
        String mensagem = recebidas.poll(3, TimeUnit.SECONDS);
        if (mensagem == null) {
            throw new AssertionError("Nenhuma mensagem recebida em 3 s");
        }
        return mensagem;
    }

    /** Código de fechamento enviado pelo servidor. */
    int codigoFechamento(long timeoutMs) throws Exception {
        return fechamento.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    boolean fechada() {
        return fechamento.isDone();
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence dados, boolean ultimo) {
        parcial.append(dados);
        if (ultimo) {
            recebidas.add(parcial.toString());
            parcial.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int codigo, String motivo) {
        fechamento.complete(codigo);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable erro) {
        fechamento.completeExceptionally(erro);
    }

    @Override
    public void close() {
        if (!socket.isOutputClosed()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(e -> null);
        }
        socket.abort();
    }
}
