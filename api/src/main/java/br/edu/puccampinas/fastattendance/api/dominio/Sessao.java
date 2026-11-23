/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.dominio;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.MotivoEncerramento;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Sessão de chamada: uma aula aberta pelo professor em um dos três modos.
 *
 * @param segredoTotp segredo Base32 da sessão (RFC 6238), compartilhado só com o professor
 * @param salaId      obrigatório no modo QR Code
 */
@Document(Colecoes.SESSOES)
public record Sessao(
        @Id String id,
        String turmaId,
        String professorId,
        ModoChamada modo,
        String salaId,
        String segredoTotp,
        StatusSessao status,
        Instant abertaEm,
        Instant encerradaEm,
        MotivoEncerramento motivoEncerramento) {
}
