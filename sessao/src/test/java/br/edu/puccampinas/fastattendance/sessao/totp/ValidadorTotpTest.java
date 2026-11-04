package br.edu.puccampinas.fastattendance.sessao.totp;

import br.edu.puccampinas.fastattendance.sessao.totp.ValidadorTotp.Resultado;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorTotpTest {

    /** Segredo ASCII de 32 bytes dos vetores oficiais da RFC 6238 (Apêndice B, HMAC-SHA256). */
    private static final byte[] CHAVE_RFC = "12345678901234567890123456789012".getBytes(StandardCharsets.US_ASCII);

    private static final long[] TEMPOS_RFC = {59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
    private static final String[] CODIGOS_RFC = {"46119246", "68084774", "67062674", "91819424", "90698825", "77737706"};

    private static final long AGORA = 1_000_000L; // janela atual: 66666 (período de 15 s)
    private static final int PERIODO = SegredoTotp.PERIODO_SEGUNDOS;
    private static final String SEGREDO = SegredoTotp.base32(CHAVE_RFC);
    private static final String OUTRO_SEGREDO = SegredoTotp.base32(
            "outro-segredo-de-teste-123456789".getBytes(StandardCharsets.US_ASCII));

    @Test
    void confereOsVetoresOficiaisDaRfc6238ComHmacSha256() {
        for (int i = 0; i < TEMPOS_RFC.length; i++) {
            assertEquals(CODIGOS_RFC[i],
                    ValidadorTotp.gerar(CHAVE_RFC, TEMPOS_RFC[i], 8, 30, 0),
                    "instante " + TEMPOS_RFC[i]);
        }
    }

    @Test
    void aceitaTokenDaJanelaAtual() {
        String token = ValidadorTotp.gerar(SEGREDO, AGORA);

        assertEquals(Resultado.VALIDO, ValidadorTotp.validar(SEGREDO, token, AGORA));
    }

    @Test
    void aceitaTokenDaJanelaAnterior() {
        String token = ValidadorTotp.gerar(SEGREDO, AGORA - PERIODO);

        assertEquals(Resultado.VALIDO, ValidadorTotp.validar(SEGREDO, token, AGORA));
    }

    @Test
    void marcaTokenDeJanelaMaisAntigaComoExpirado() {
        String doisPeriodosAtras = ValidadorTotp.gerar(SEGREDO, AGORA - 2L * PERIODO);
        String limiteDeExpiracao = ValidadorTotp.gerar(SEGREDO,
                AGORA - (ValidadorTotp.JANELAS_ACEITAS - 1 + ValidadorTotp.JANELAS_EXPIRADAS) * (long) PERIODO);

        assertEquals(Resultado.TOKEN_EXPIRADO, ValidadorTotp.validar(SEGREDO, doisPeriodosAtras, AGORA));
        assertEquals(Resultado.TOKEN_EXPIRADO, ValidadorTotp.validar(SEGREDO, limiteDeExpiracao, AGORA));
    }

    @Test
    void recusaTokenAlemDaJanelaDeExpiracao() {
        long alem = AGORA - (ValidadorTotp.JANELAS_ACEITAS + ValidadorTotp.JANELAS_EXPIRADAS) * (long) PERIODO;
        String token = ValidadorTotp.gerar(SEGREDO, alem);

        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, token, AGORA));
    }

    @Test
    void recusaTokenDeJanelaFutura() {
        String token = ValidadorTotp.gerar(SEGREDO, AGORA + PERIODO);

        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, token, AGORA));
    }

    @Test
    void recusaTokenDeOutraSessao() {
        String token = ValidadorTotp.gerar(OUTRO_SEGREDO, AGORA);

        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, token, AGORA));
    }

    @Test
    void recusaTokenComFormatoInvalido() {
        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, null, AGORA));
        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, "", AGORA));
        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, "12345", AGORA));
        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, "1234567", AGORA));
        assertEquals(Resultado.TOKEN_INVALIDO, ValidadorTotp.validar(SEGREDO, "12a456", AGORA));
    }

    @Test
    void validacaoConcluiEmMenosDeUmSegundo() {
        String atual = ValidadorTotp.gerar(SEGREDO, AGORA);
        String expirado = ValidadorTotp.gerar(SEGREDO, AGORA - 3L * PERIODO);

        long inicio = System.nanoTime();
        ValidadorTotp.validar(SEGREDO, atual, AGORA);
        ValidadorTotp.validar(SEGREDO, expirado, AGORA);
        ValidadorTotp.validar(SEGREDO, "000000", AGORA);
        long decorrido = System.nanoTime() - inicio;

        assertTrue(decorrido < 1_000_000_000L, "validação levou " + decorrido + " ns");
    }
}
