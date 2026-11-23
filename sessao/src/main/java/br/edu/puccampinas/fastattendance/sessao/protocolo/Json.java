/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.protocolo;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Serialização JSON das mensagens do protocolo (Jackson 3). Thread-safe. */
public final class Json {

    private final JsonMapper mapper = JsonMapper.builder()
            // Campos desconhecidos são ignorados para permitir evolução compatível da versão 1
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Permite serializar corpos vazios ({}) como em ping/pong
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    /** @throws tools.jackson.core.JacksonException se o texto não for JSON válido */
    public JsonNode ler(String texto) {
        return mapper.readTree(texto);
    }

    /** @throws tools.jackson.core.JacksonException se os dados não couberem no tipo */
    public <T> T converter(JsonNode no, Class<T> tipo) {
        return mapper.treeToValue(no, tipo);
    }

    public String escrever(Object valor) {
        return mapper.writeValueAsString(valor);
    }
}
