/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo;

/** Constantes do protocolo de mensagens (ver docs/protocolo-v1.md). */
public final class Protocolo {

    public static final int VERSAO = 1;

    /** Prazo para o cliente enviar {@code auth} após abrir a conexão. */
    public static final long PRAZO_AUTENTICACAO_MS = 5_000;

    /** Intervalo de {@code ping} esperado do cliente. */
    public static final long INTERVALO_PING_MS = 25_000;

    /** Conexões sem tráfego por este tempo são encerradas pelo servidor. */
    public static final long TIMEOUT_INATIVIDADE_MS = 60_000;

    /** Prefixo do conteúdo do QR Code: {@code FA1:<sessaoId>:<token>}. */
    public static final String PREFIXO_QRCODE = "FA1";

    private Protocolo() {
    }
}
