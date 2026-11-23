/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Geração e validação de tokens TOTP (RFC 6238, HMAC-SHA256) conforme docs/protocolo-v1.md, seções 4.8 e 5.3.
 *
 * <p>Janelas: a atual (deslocamento 0) e a imediatamente anterior (deslocamento 1) são aceitas.
 * Um token que confere com uma janela mais antiga, até {@link #JANELAS_EXPIRADAS} janelas além
 * das aceitas, é reconhecido como {@link Resultado#TOKEN_EXPIRADO}. Qualquer outro token é
 * {@link Resultado#TOKEN_INVALIDO}.
 */
public final class ValidadorTotp {

    /** Janelas aceitas: a atual e a imediatamente anterior. */
    static final int JANELAS_ACEITAS = 2;

    /** Janelas além das aceitas que ainda são reconhecidas como expiradas. */
    static final int JANELAS_EXPIRADAS = 4;

    public enum Resultado {
        VALIDO,
        TOKEN_INVALIDO,
        TOKEN_EXPIRADO
    }

    private ValidadorTotp() {
    }

    /** Código de 6 dígitos da sessão para o instante informado (em segundos Unix). */
    public static String gerar(String segredoBase32, long instanteSegundos) {
        return gerar(SegredoTotp.decodificarBase32(segredoBase32), instanteSegundos,
                SegredoTotp.DIGITOS, SegredoTotp.PERIODO_SEGUNDOS, SegredoTotp.T0);
    }

    /** Valida o token de 6 dígitos da sessão no instante informado (em segundos Unix). */
    public static Resultado validar(String segredoBase32, String token, long instanteSegundos) {
        return validar(SegredoTotp.decodificarBase32(segredoBase32), token, instanteSegundos,
                SegredoTotp.DIGITOS, SegredoTotp.PERIODO_SEGUNDOS, SegredoTotp.T0);
    }

    static String gerar(byte[] chave, long instanteSegundos, int digitos, int periodo, long t0) {
        return codigo(chave, contador(instanteSegundos, t0, periodo), digitos);
    }

    static Resultado validar(byte[] chave, String token, long instanteSegundos,
                             int digitos, int periodo, long t0) {
        if (token == null || token.length() != digitos || !somenteDigitos(token)) {
            return Resultado.TOKEN_INVALIDO;
        }
        byte[] esperadoEntrada = token.getBytes(StandardCharsets.US_ASCII);
        long janelaAtual = contador(instanteSegundos, t0, periodo);

        boolean aceito = false;
        boolean expirado = false;
        // Percorre todas as janelas sem interromper ao achar um match, para não vazar a posição do acerto.
        for (int deslocamento = 0; deslocamento < JANELAS_ACEITAS + JANELAS_EXPIRADAS; deslocamento++) {
            long janela = janelaAtual - deslocamento;
            if (janela < 0) {
                break;
            }
            boolean confere = MessageDigest.isEqual(
                    esperadoEntrada, codigo(chave, janela, digitos).getBytes(StandardCharsets.US_ASCII));
            if (deslocamento < JANELAS_ACEITAS) {
                aceito |= confere;
            } else {
                expirado |= confere;
            }
        }

        if (aceito) {
            return Resultado.VALIDO;
        }
        return expirado ? Resultado.TOKEN_EXPIRADO : Resultado.TOKEN_INVALIDO;
    }

    /** Contador T = floor((instante - t0) / periodo). */
    static long contador(long instanteSegundos, long t0, int periodo) {
        return Math.floorDiv(instanteSegundos - t0, (long) periodo);
    }

    /** HOTP (RFC 4226) com HMAC-SHA256 e truncamento dinâmico. */
    static String codigo(byte[] chave, long contador, int digitos) {
        try {
            Mac mac = Mac.getInstance(SegredoTotp.ALGORITMO);
            mac.init(new SecretKeySpec(chave, SegredoTotp.ALGORITMO));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(contador).array());
            int deslocamento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[deslocamento] & 0x7f) << 24)
                    | ((hash[deslocamento + 1] & 0xff) << 16)
                    | ((hash[deslocamento + 2] & 0xff) << 8)
                    | (hash[deslocamento + 3] & 0xff);
            int modulo = (int) Math.pow(10, digitos);
            return String.format("%0" + digitos + "d", binario % modulo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível na JVM", e);
        }
    }

    private static boolean somenteDigitos(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
