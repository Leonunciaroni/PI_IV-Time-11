package br.edu.puccampinas.fastattendance.api.dominio;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Sala física, usada na validação de distância do modo QR Code (fórmula de Haversine).
 *
 * @param raioMetros distância máxima aceita entre o aluno e as coordenadas da sala
 */
@Document(Colecoes.SALAS)
public record Sala(
        @Id String id,
        String nome,
        double latitude,
        double longitude,
        double raioMetros) {
}
