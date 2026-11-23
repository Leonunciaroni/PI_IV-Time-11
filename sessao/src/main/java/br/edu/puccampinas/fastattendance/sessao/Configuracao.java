/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;

import java.time.Duration;
import java.util.Map;

/**
 * Configuração do servidor de sessão, lida de variáveis de ambiente. A porta não está aqui: é o
 * argumento {@code [PORTA]} de {@link Servidor}.
 *
 * @param jwtSegredo         {@code JWT_SECRET}, o mesmo da API
 * @param mongoUri           {@code MONGODB_URI}, o mesmo banco da API
 * @param prazoAutenticacao  {@code SESSAO_PRAZO_AUTH_MS}: tempo para o cliente se autenticar após conectar
 * @param timeoutInatividade {@code SESSAO_TIMEOUT_INATIVIDADE_MS}: conexões sem tráfego são encerradas
 * @param duracaoMaxima      {@code SESSAO_DURACAO_MAXIMA_MIN} (padrão 240): sessões abertas há mais
 *                           tempo são encerradas com motivo {@code TEMPO_LIMITE}
 */
public record Configuracao(String jwtSegredo, String mongoUri, Duration prazoAutenticacao,
                           Duration timeoutInatividade, Duration duracaoMaxima) {

    static final String MONGODB_URI_PADRAO = "mongodb://localhost:27017/fast_attendance?directConnection=true";

    public static Configuracao doAmbiente(Map<String, String> ambiente) {
        String segredo = ambiente.get("JWT_SECRET");
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente JWT_SECRET (a mesma da API)");
        }
        String mongoUri = ambiente.get("MONGODB_URI");
        return new Configuracao(
                segredo,
                mongoUri == null || mongoUri.isBlank() ? MONGODB_URI_PADRAO : mongoUri.strip(),
                Duration.ofMillis(inteiro(ambiente, "SESSAO_PRAZO_AUTH_MS", (int) Protocolo.PRAZO_AUTENTICACAO_MS)),
                Duration.ofMillis(inteiro(ambiente, "SESSAO_TIMEOUT_INATIVIDADE_MS",
                        (int) Protocolo.TIMEOUT_INATIVIDADE_MS)),
                Duration.ofMinutes(inteiro(ambiente, "SESSAO_DURACAO_MAXIMA_MIN", 240)));
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
