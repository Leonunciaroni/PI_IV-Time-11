package br.edu.puccampinas.fastattendance.ponte;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Limite de mensagens por conexão do navegador (docs/protocolo-v1.md, seções 1 e 7).
 * <p>
 * Conta todo frame de texto recebido, inclusive os que a ponte recusa por validação, como no antigo
 * {@code RoteadorDeMensagens}. Por isso a ponte nunca encaminha ao servidor mais do que o limite.
 * Os valores são os mesmos do {@code sessao/servidor/LimitadorDeMensagens}.
 */
final class LimitadorDeMensagens {

    static final int MAXIMO = 60;
    static final Duration JANELA = Duration.ofSeconds(10);
    static final int REJEICOES_ATE_FECHAR = 5;

    private final Clock relogio;
    private final Deque<Long> aceitas = new ArrayDeque<>();
    private int rejeicoesSeguidas;

    LimitadorDeMensagens(Clock relogio) {
        this.relogio = relogio;
    }

    /** Registra um frame recebido; devolve {@code false} se ele excede o limite. */
    boolean registrar() {
        long agora = relogio.millis();
        while (!aceitas.isEmpty() && agora - aceitas.peekFirst() >= JANELA.toMillis()) {
            aceitas.pollFirst();
        }
        if (aceitas.size() >= MAXIMO) {
            rejeicoesSeguidas++;
            return false;
        }
        aceitas.addLast(agora);
        rejeicoesSeguidas = 0;
        return true;
    }

    /** Verdadeiro quando o cliente insiste em exceder o limite e a conexão deve ser fechada. */
    boolean excessoRepetido() {
        return rejeicoesSeguidas >= REJEICOES_ATE_FECHAR;
    }
}
