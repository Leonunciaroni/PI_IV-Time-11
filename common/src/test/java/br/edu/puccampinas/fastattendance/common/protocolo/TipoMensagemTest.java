package br.edu.puccampinas.fastattendance.common.protocolo;

import br.edu.puccampinas.fastattendance.common.protocolo.dados.SessaoModoAlterado;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.SessaoTrocarModo;
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

    @Test
    void trocaDeModoEhPedidoDoClienteERespostaDoServidor() {
        TipoMensagem pedido = TipoMensagem.de("sessao.trocar_modo").orElseThrow();
        assertEquals(Direcao.CLIENTE_PARA_SERVIDOR, pedido.direcao());
        assertEquals(SessaoTrocarModo.class, pedido.classeDados());

        TipoMensagem resposta = TipoMensagem.de("sessao.modo_alterado").orElseThrow();
        assertEquals(Direcao.SERVIDOR_PARA_CLIENTE, resposta.direcao());
        assertEquals(SessaoModoAlterado.class, resposta.classeDados());
    }

    @Test
    void valoresDoCampoTipoSaoUnicos() {
        assertEquals(TipoMensagem.values().length,
                Arrays.stream(TipoMensagem.values()).map(TipoMensagem::valor).distinct().count());
    }
}
