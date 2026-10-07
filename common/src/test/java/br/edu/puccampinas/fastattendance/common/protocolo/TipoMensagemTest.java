package br.edu.puccampinas.fastattendance.common.protocolo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TipoMensagemTest {

    @Test
    void localizaTipoPeloValorDoCampoTipo() {
        assertEquals(TipoMensagem.CHECKIN, TipoMensagem.de("checkin").orElseThrow());
        assertEquals(TipoMensagem.PRESENCA_MARCAR_LOTE, TipoMensagem.de("presenca.marcar_lote").orElseThrow());
    }

    @Test
    void tipoDesconhecidoOuNuloRetornaVazio() {
        assertTrue(TipoMensagem.de("inexistente").isEmpty());
        assertTrue(TipoMensagem.de(null).isEmpty());
    }

    @Test
    void todoTipoTemClasseDeDados() {
        assertTrue(Arrays.stream(TipoMensagem.values()).allMatch(t -> t.classeDados() != null));
    }
}
