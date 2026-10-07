package br.edu.puccampinas.fastattendance.api.dominio;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Registro de presença de um aluno em uma sessão. Há no máximo um documento por
 * {@code {alunoId, sessaoId}}, garantido por índice único.
 *
 * @param presente      no modo clássico o professor pode desmarcar um aluno ({@code false})
 * @param dispositivoId preenchido nos modos QR Code e som; único por sessão
 * @param latitude      preenchido no modo QR Code
 */
@Document(Colecoes.PRESENCAS)
public record Presenca(
        @Id String id,
        String sessaoId,
        String turmaId,
        String alunoId,
        boolean presente,
        OrigemPresenca origem,
        String dispositivoId,
        Double latitude,
        Double longitude,
        Instant registradoEm) {
}
