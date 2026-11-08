package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;

/**
 * Aluno → servidor: confirma presença com o token lido do QR Code ou do som.
 * Coordenadas são obrigatórias no canal QRCODE e ignoradas no canal SOM.
 */
public record Checkin(
        String sessaoId,
        String token,
        OrigemPresenca canal,
        String dispositivoId,
        Double latitude,
        Double longitude,
        Double precisaoMetros) {
}
