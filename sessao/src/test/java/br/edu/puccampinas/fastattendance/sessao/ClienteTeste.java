package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.Erro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.sessao.servidor.Parceiro;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cliente de teste do transporte por sockets: um {@link Parceiro} sobre um {@link Socket}, como o
 * cliente do material da disciplina. Envia e recebe os comunicados Java diretamente.
 */
final class ClienteTeste implements AutoCloseable {

    /** Prazo para uma mensagem esperada chegar. */
    private static final int PRAZO_MS = 3_000;

    private final Socket socket;
    private final Parceiro parceiro;

    ClienteTeste(int porta) throws Exception {
        socket = new Socket(InetAddress.getLoopbackAddress(), porta);
        socket.setTcpNoDelay(true);
        // Mesma ordem do cliente do modelo: transmissor primeiro, depois receptor
        ObjectOutputStream transmissor = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream receptor = new ObjectInputStream(socket.getInputStream());
        parceiro = new Parceiro(socket, receptor, transmissor);
    }

    void enviar(Comunicado comunicado) throws Exception {
        parceiro.receba(comunicado);
    }

    /** Próximo comunicado; falha se nada chegar em 3 s ou se a conexão for fechada. */
    Comunicado proximo() {
        try {
            socket.setSoTimeout(PRAZO_MS);
            return parceiro.envie();
        } catch (Exception erro) {
            throw new AssertionError("Nenhum comunicado em 3 s, ou conexão fechada", erro);
        }
    }

    /** Próximo comunicado, que precisa ser do tipo esperado. */
    <T extends Comunicado> T proximo(Class<T> tipo) {
        Comunicado recebido = proximo();
        if (!tipo.isInstance(recebido)) {
            throw new AssertionError("Esperado " + tipo.getSimpleName() + ", veio " + recebido);
        }
        return tipo.cast(recebido);
    }

    /** Próximo comunicado, que precisa ser um {@link Erro} com o código indicado. */
    Erro proximoErro(CodigoErro codigoEsperado) {
        Erro erro = proximo(Erro.class);
        assertEquals(codigoEsperado, erro.getCodigo(), erro.toString());
        return erro;
    }

    /**
     * Próximo comunicado, ou {@code null} se nada chegar no prazo (para afirmar que nada foi enviado).
     * Uma conexão fechada antes do prazo é falha, e não um silêncio.
     */
    Comunicado aguardar(long ms) throws Exception {
        socket.setSoTimeout((int) ms);
        long inicio = System.nanoTime();
        try {
            return parceiro.envie();
        } catch (Exception erro) {
            if (System.nanoTime() - inicio < TimeUnit.MILLISECONDS.toNanos(ms)) {
                throw new AssertionError("Conexão fechada antes do prazo de " + ms + " ms", erro);
            }
            return null;
        }
    }

    /** Afirma que o servidor fechou a conexão dentro do prazo: a leitura falha antes de o prazo estourar. */
    void aguardarFechamento(long ms) throws Exception {
        socket.setSoTimeout((int) ms);
        long inicio = System.nanoTime();
        try {
            Comunicado inesperado = parceiro.envie();
            fail("Conexão ainda aberta; recebido: " + inesperado);
        } catch (AssertionError erro) {
            throw erro;
        } catch (Exception erro) {
            long decorrido = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            if (decorrido >= ms * 0.9) {
                throw new AssertionError("Conexão não foi fechada dentro de " + ms + " ms", erro);
            }
        }
    }

    @Override
    public void close() {
        try {
            parceiro.adeus();
        } catch (Exception erro) {
            // Já fechada pelo servidor: só falta liberar o socket
        }
        try {
            socket.close();
        } catch (Exception erro) {
            // Idem
        }
    }
}
