package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

/** Servidor → professor: sessão aberta. */
public record SessaoAberta(
        String sessaoId,
        String turmaId,
        ModoChamada modo,
        long abertaEm,
        ConfiguracaoTotp totp) {
}
