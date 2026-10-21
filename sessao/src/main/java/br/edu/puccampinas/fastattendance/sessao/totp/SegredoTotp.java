package br.edu.puccampinas.fastattendance.sessao.totp;

import br.edu.puccampinas.fastattendance.common.protocolo.dados.ConfiguracaoTotp;

import java.security.SecureRandom;

/** Segredo e parâmetros TOTP de uma sessão (docs/protocolo-v1.md, seção 5.3). */
public final class SegredoTotp {

    public static final String ALGORITMO = "HmacSHA256";
    public static final int DIGITOS = 6;
    public static final int PERIODO_SEGUNDOS = 15;
    public static final long T0 = 0;

    /** 160 bits, o tamanho recomendado pela RFC 4226 para o segredo compartilhado. */
    static final int BYTES = 20;

    private static final char[] ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private SegredoTotp() {
    }

    /** Novo segredo aleatório, em Base32 (32 caracteres). */
    public static String gerar(SecureRandom aleatorio) {
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        return base32(bytes);
    }

    public static ConfiguracaoTotp configuracao(String segredo) {
        return new ConfiguracaoTotp(segredo, ALGORITMO, DIGITOS, PERIODO_SEGUNDOS, T0);
    }

    /** Base32 da RFC 4648, sem o preenchimento com {@code =}. */
    static String base32(byte[] bytes) {
        StringBuilder texto = new StringBuilder((bytes.length * 8 + 4) / 5);
        int acumulado = 0;
        int bits = 0;
        for (byte b : bytes) {
            acumulado = (acumulado << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                texto.append(ALFABETO_BASE32[(acumulado >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            texto.append(ALFABETO_BASE32[(acumulado << (5 - bits)) & 31]);
        }
        return texto.toString();
    }
}
