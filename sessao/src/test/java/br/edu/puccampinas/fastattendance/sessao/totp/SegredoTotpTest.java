package br.edu.puccampinas.fastattendance.sessao.totp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegredoTotpTest {

    @Test
    void codificaOsVetoresDaRfc4648SemPreenchimento() {
        String[][] vetores = {
                {"", ""}, {"f", "MY"}, {"fo", "MZXQ"}, {"foo", "MZXW6"},
                {"foob", "MZXW6YQ"}, {"fooba", "MZXW6YTB"}, {"foobar", "MZXW6YTBOI"}};
        for (String[] vetor : vetores) {
            assertEquals(vetor[1], SegredoTotp.base32(vetor[0].getBytes(StandardCharsets.US_ASCII)), vetor[0]);
        }
    }

    @Test
    void geraSegredosDe160BitsDiferentesACadaChamada() {
        SecureRandom aleatorio = new SecureRandom();
        String primeiro = SegredoTotp.gerar(aleatorio);
        String segundo = SegredoTotp.gerar(aleatorio);

        assertEquals(32, primeiro.length());
        assertTrue(primeiro.matches("[A-Z2-7]{32}"), primeiro);
        assertNotEquals(primeiro, segundo);
    }
}
