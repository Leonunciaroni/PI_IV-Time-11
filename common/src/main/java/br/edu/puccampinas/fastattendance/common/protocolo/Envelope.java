/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope comum a todas as mensagens do protocolo.
 *
 * @param v     versão do protocolo
 * @param tipo  tipo da mensagem (ver {@link TipoMensagem})
 * @param id    identificador de correlação gerado pelo remetente (requisições)
 * @param ref   {@code id} da requisição respondida (respostas)
 * @param ts    instante de envio, em milissegundos Unix
 * @param dados corpo específico do tipo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Envelope<T>(int v, String tipo, String id, String ref, long ts, T dados) {

    /** Cria uma resposta à requisição {@code ref}. */
    public static <T> Envelope<T> resposta(TipoMensagem tipo, String ref, T dados) {
        return new Envelope<>(Protocolo.VERSAO, tipo.valor(), null, ref, System.currentTimeMillis(), dados);
    }

    /** Cria um evento (push), sem {@code ref}. */
    public static <T> Envelope<T> evento(TipoMensagem tipo, T dados) {
        return new Envelope<>(Protocolo.VERSAO, tipo.valor(), null, null, System.currentTimeMillis(), dados);
    }
}
