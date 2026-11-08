package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;

import java.time.Duration;
import java.util.Map;

/**
 * Configuração da ponte, lida de variáveis de ambiente e do argumento {@code [PORTA]}.
 *
 * @param porta              porta do WebSocket que os navegadores acessam ({@code argumento}, {@code PONTE_PORTA}, padrão 8082)
 * @param sessaoHost         {@code SESSAO_HOST}: nome do servidor de sessão (padrão {@code sessao}); pode ter várias réplicas
 * @param sessaoPorta        {@code SESSAO_PORTA}: porta TCP do servidor de sessão (padrão 8081)
 * @param prazoAutenticacao  {@code SESSAO_PRAZO_AUTH_MS}: prazo do {@code auth} após abrir o WebSocket; deve ser o do servidor
 * @param timeoutInatividade {@code SESSAO_TIMEOUT_INATIVIDADE_MS}: inatividade do servidor; deve ser o dele (usado na heurística de fechamento)
 */
public record ConfiguracaoPonte(int porta, String sessaoHost, int sessaoPorta, Duration prazoAutenticacao,
                                Duration timeoutInatividade) {

    static final int PORTA_PADRAO = 8082;
    static final String SESSAO_HOST_PADRAO = "sessao";
    static final int SESSAO_PORTA_PADRAO = 8081;

    /** Monta a configuração; {@code argumentoPorta} é o primeiro argumento de linha de comando, ou {@code null}. */
    public static ConfiguracaoPonte doAmbiente(Map<String, String> ambiente, String argumentoPorta) {
        int porta = argumentoPorta != null
                ? porta(argumentoPorta, "argumento [PORTA]")
                : inteiro(ambiente, "PONTE_PORTA", PORTA_PADRAO);
        String host = ambiente.get("SESSAO_HOST");
        return new ConfiguracaoPonte(
                porta,
                host == null || host.isBlank() ? SESSAO_HOST_PADRAO : host.strip(),
                inteiro(ambiente, "SESSAO_PORTA", SESSAO_PORTA_PADRAO),
                Duration.ofMillis(inteiro(ambiente, "SESSAO_PRAZO_AUTH_MS", (int) Protocolo.PRAZO_AUTENTICACAO_MS)),
                Duration.ofMillis(inteiro(ambiente, "SESSAO_TIMEOUT_INATIVIDADE_MS",
                        (int) Protocolo.TIMEOUT_INATIVIDADE_MS)));
    }

    private static int inteiro(Map<String, String> ambiente, String nome, int padrao) {
        String valor = ambiente.get(nome);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        return porta(valor, nome);
    }

    private static int porta(String valor, String origem) {
        try {
            int numero = Integer.parseInt(valor.strip());
            if (numero < 0 || numero > 65535) {
                throw new IllegalStateException("Valor fora de faixa para " + origem + ": " + valor);
            }
            return numero;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Valor inválido para " + origem + ": " + valor, e);
        }
    }
}
