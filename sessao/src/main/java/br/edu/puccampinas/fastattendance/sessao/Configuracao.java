package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;

import java.time.Duration;
import java.util.Map;

/**
 * Configuração do servidor de sessão, lida de variáveis de ambiente.
 *
 * @param porta              {@code SESSAO_PORTA} (padrão 8081)
 * @param jwtSegredo         {@code JWT_SECRET}, o mesmo da API
 * @param prazoAutenticacao  {@code SESSAO_PRAZO_AUTH_MS}: tempo para o cliente enviar {@code auth}
 * @param timeoutInatividade {@code SESSAO_TIMEOUT_INATIVIDADE_MS}: conexões sem tráfego são fechadas
 */
public record Configuracao(int porta, String jwtSegredo, Duration prazoAutenticacao, Duration timeoutInatividade) {

    public static Configuracao doAmbiente(Map<String, String> ambiente) {
        String segredo = ambiente.get("JWT_SECRET");
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente JWT_SECRET (a mesma da API)");
        }
        return new Configuracao(
                inteiro(ambiente, "SESSAO_PORTA", 8081),
                segredo,
                Duration.ofMillis(inteiro(ambiente, "SESSAO_PRAZO_AUTH_MS", (int) Protocolo.PRAZO_AUTENTICACAO_MS)),
                Duration.ofMillis(inteiro(ambiente, "SESSAO_TIMEOUT_INATIVIDADE_MS",
                        (int) Protocolo.TIMEOUT_INATIVIDADE_MS)));
    }

    private static int inteiro(Map<String, String> ambiente, String nome, int padrao) {
        String valor = ambiente.get(nome);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            return Integer.parseInt(valor.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Valor inválido para " + nome + ": " + valor, e);
        }
    }
}
