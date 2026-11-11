package br.edu.puccampinas.fastattendance.ponte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisorDeFechamentoTest {

    private static final long AGORA = 1_800_000_000_000L;
    private static final long TIMEOUT = 60_000;

    @Test
    void semAutenticacaoConcluidaFechaComAutenticacaoFalhou() {
        var f = DecisorDeFechamento.aposFimDoServidor(false, null, AGORA, AGORA, TIMEOUT);
        assertEquals(4001, f.codigo());
    }

    @Test
    void tokenJaVencidoFechaComTokenExpirou() {
        var f = DecisorDeFechamento.aposFimDoServidor(true, AGORA - 1, AGORA, AGORA, TIMEOUT);
        assertEquals(4003, f.codigo());
    }

    @Test
    void tokenQuaseVencidoConsideraAFolgaDeUmSegundo() {
        // Dentro da folga: o servidor pode ter fechado um pouco antes do instante exato
        var f = DecisorDeFechamento.aposFimDoServidor(true, AGORA + 500, AGORA, AGORA, TIMEOUT);
        assertEquals(4003, f.codigo());
        // Ainda longe do fim: não é expiração
        var g = DecisorDeFechamento.aposFimDoServidor(true, AGORA + 5_000, AGORA, AGORA, TIMEOUT);
        assertEquals(1001, g.codigo());
    }

    @Test
    void semFrameHaMaisDoTimeoutFechaComoInatividade() {
        var f = DecisorDeFechamento.aposFimDoServidor(true, AGORA + 3_600_000, AGORA, AGORA - TIMEOUT, TIMEOUT);
        assertEquals(1000, f.codigo());
        assertEquals(DecisorDeFechamento.MOTIVO_INATIVIDADE, f.motivo());
    }

    @Test
    void quedaRecenteDoServidorFechaComoReinicio() {
        var f = DecisorDeFechamento.aposFimDoServidor(true, AGORA + 3_600_000, AGORA, AGORA - 1_000, TIMEOUT);
        assertEquals(1001, f.codigo());
    }

    @Test
    void expiracaoTemPrecedenciaSobreInatividade() {
        var f = DecisorDeFechamento.aposFimDoServidor(true, AGORA - 1, AGORA, AGORA - TIMEOUT - 1, TIMEOUT);
        assertEquals(4003, f.codigo());
    }
}
