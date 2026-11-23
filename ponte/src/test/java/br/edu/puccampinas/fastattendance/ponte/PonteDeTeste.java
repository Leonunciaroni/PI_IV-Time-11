/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Ponte em porta efêmera, ligada ao servidor de sessão de teste indicado. */
final class PonteDeTeste implements AutoCloseable {

    private final Ponte ponte;
    private final int porta;

    PonteDeTeste(int portaSessao, Duration prazoAutenticacao, Duration timeoutInatividade) throws Exception {
        ConfiguracaoPonte config = new ConfiguracaoPonte(0, "127.0.0.1", portaSessao, prazoAutenticacao,
                timeoutInatividade);
        ponte = new Ponte(config);
        ponte.start();
        porta = ponte.aguardarInicio(5, TimeUnit.SECONDS);
    }

    int porta() {
        return porta;
    }

    @Override
    public void close() throws Exception {
        ponte.encerrar(500);
    }
}
