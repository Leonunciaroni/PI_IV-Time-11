package br.edu.puccampinas.fastattendance.ponte;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Leitura e escrita JSON do protocolo v1 (Jackson 3). Thread-safe.
 * <p>
 * Cópia da classe de mesmo nome do {@code sessao}: o {@code common} não tem Jackson databind, e a
 * ponte só precisa ler e escrever árvores.
 */
public final class Json {

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** @throws tools.jackson.core.JacksonException se o texto não for JSON válido */
    public JsonNode ler(String texto) {
        return mapper.readTree(texto);
    }

    public String escrever(Object valor) {
        return mapper.writeValueAsString(valor);
    }
}
