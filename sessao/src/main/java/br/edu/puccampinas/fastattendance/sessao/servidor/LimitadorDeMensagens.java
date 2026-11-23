/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.servidor;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Limite de mensagens por conexão (docs/protocolo-v1.md, seções 7 e 8).
 * <p>
 * Aceita até {@link #MAXIMO} mensagens em qualquer janela de {@link #JANELA}. As excedentes são
 * recusadas com {@code LIMITE_EXCEDIDO} e não chegam a ser processadas. Depois de
 * {@link #REJEICOES_ATE_FECHAR} recusas seguidas, a conexão é fechada com {@code 4008}.
 * <p>
 * Os valores não são definidos pelo protocolo; ficam aqui como decisão de implementação.
 */
public final class LimitadorDeMensagens {

    public static final int MAXIMO = 60;
    public static final Duration JANELA = Duration.ofSeconds(10);
    public static final int REJEICOES_ATE_FECHAR = 5;

    private final Clock relogio;
    /** Instantes (ms) das mensagens aceitas dentro da janela atual, do mais antigo ao mais recente. */
    private final Deque<Long> aceitas = new ArrayDeque<>();
    private int rejeicoesSeguidas;

    public LimitadorDeMensagens(Clock relogio) {
        this.relogio = relogio;
    }

    /** Registra uma mensagem recebida; devolve {@code false} se ela excede o limite. */
    public boolean registrar() {
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
    public boolean excessoRepetido() {
        return rejeicoesSeguidas >= REJEICOES_ATE_FECHAR;
    }
}
