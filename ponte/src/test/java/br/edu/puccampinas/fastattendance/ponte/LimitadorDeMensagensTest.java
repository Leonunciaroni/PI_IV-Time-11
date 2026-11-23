/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LimitadorDeMensagensTest {

    /** Relógio controlável para o teste. */
    private static final class Relogio {
        long agora = 0;
        Clock clock() {
            return new Clock() {
                @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return Instant.ofEpochMilli(agora); }
            };
        }
    }

    @Test
    void aceitaAteOLimiteEmUmaJanela() {
        Relogio relogio = new Relogio();
        LimitadorDeMensagens limitador = new LimitadorDeMensagens(relogio.clock());
        for (int i = 0; i < LimitadorDeMensagens.MAXIMO; i++) {
            assertTrue(limitador.registrar());
        }
        assertFalse(limitador.registrar());
        assertFalse(limitador.excessoRepetido());
    }

    @Test
    void fechaDepoisDeRecusasSeguidas() {
        Relogio relogio = new Relogio();
        LimitadorDeMensagens limitador = new LimitadorDeMensagens(relogio.clock());
        for (int i = 0; i < LimitadorDeMensagens.MAXIMO; i++) {
            limitador.registrar();
        }
        for (int i = 0; i < LimitadorDeMensagens.REJEICOES_ATE_FECHAR; i++) {
            assertFalse(limitador.registrar());
        }
        assertTrue(limitador.excessoRepetido());
    }

    @Test
    void liberaQuandoAJanelaPassa() {
        Relogio relogio = new Relogio();
        LimitadorDeMensagens limitador = new LimitadorDeMensagens(relogio.clock());
        for (int i = 0; i < LimitadorDeMensagens.MAXIMO; i++) {
            limitador.registrar();
        }
        relogio.agora = LimitadorDeMensagens.JANELA.toMillis();
        assertTrue(limitador.registrar());
    }
}
