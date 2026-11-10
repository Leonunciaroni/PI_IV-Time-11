package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;

/**
 * Escolhe o código de fechamento do WebSocket quando o servidor de sessão cai sem dizer o motivo.
 * <p>
 * O TCP não tem código de fechamento, então quando o socket termina sem um comunicado de explicação
 * (ComunicadoDeDesligamento ou Erro), a ponte decide pelas informações que tem. A ordem é esta:
 * <ol>
 *   <li>Sem autenticação concluída: {@code 4001}. Vale também para queda antes do {@code auth.ok}.</li>
 *   <li>Token expirado: {@code 4003} quando o instante atual alcança o {@code expiraEm} do
 *       {@code auth.ok} (com {@link #TOLERANCIA_EXPIRACAO_MS} de folga para diferença de relógio e de
 *       agendamento entre o servidor e a ponte). O servidor fecha exatamente no fim do token.</li>
 *   <li>Inatividade: {@code 1000} quando o último frame do navegador tem pelo menos
 *       {@code timeoutInatividade} de idade. O servidor fecha por inatividade com base no último
 *       pedido que recebeu, que a ponte encaminhou depois de receber o frame.</li>
 *   <li>Qualquer outra queda: {@code 1001}. O cliente reconecta com backoff, pelo Nginx, a outra
 *       réplica.</li>
 * </ol>
 * Esta é uma heurística: uma queda do servidor que ocorra no mesmo momento de uma dessas condições
 * recebe o código dela. Queda com mais de {@code timeoutInatividade} sem frames, por exemplo, sai
 * como {@code 1000} mesmo que a causa tenha sido outra.
 */
final class DecisorDeFechamento {

    /** Folga, em ms, para considerar o token expirado antes que o servidor feche a conexão. */
    static final long TOLERANCIA_EXPIRACAO_MS = 1_000;

    static final String MOTIVO_SEM_AUTENTICACAO = "Autenticação não concluída";
    static final String MOTIVO_TOKEN_EXPIRADO = "Token expirado";
    static final String MOTIVO_INATIVIDADE = "Inatividade";
    static final String MOTIVO_INDISPONIVEL = "Servidor de sessão indisponível";

    record Fechamento(int codigo, String motivo) {
    }

    private DecisorDeFechamento() {
    }

    /**
     * @param autenticada       se o {@code auth.ok} já foi recebido
     * @param expiraEmMs        {@code expiraEm} do {@code auth.ok} (ms Unix), ou {@code null} sem autenticação
     * @param agoraMs           instante atual (ms Unix)
     * @param ultimoFrameMs     instante (ms Unix) do último frame de texto recebido do navegador
     * @param timeoutInatividadeMs prazo de inatividade do servidor, em ms
     */
    static Fechamento aposFimDoServidor(boolean autenticada, Long expiraEmMs, long agoraMs,
                                        long ultimoFrameMs, long timeoutInatividadeMs) {
        if (!autenticada) {
            return new Fechamento(CodigoFechamento.AUTENTICACAO_FALHOU, MOTIVO_SEM_AUTENTICACAO);
        }
        if (expiraEmMs != null && agoraMs >= expiraEmMs - TOLERANCIA_EXPIRACAO_MS) {
            return new Fechamento(CodigoFechamento.TOKEN_EXPIROU, MOTIVO_TOKEN_EXPIRADO);
        }
        if (agoraMs - ultimoFrameMs >= timeoutInatividadeMs) {
            return new Fechamento(CodigoFechamento.NORMAL, MOTIVO_INATIVIDADE);
        }
        return new Fechamento(CodigoFechamento.SERVIDOR_REINICIANDO, MOTIVO_INDISPONIVEL);
    }
}
