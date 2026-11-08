package br.edu.puccampinas.fastattendance.ponte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PendentesDeRespostaTest {

    @Test
    void devolveNaOrdemDeEnvio() {
        PendentesDeResposta fila = new PendentesDeResposta();
        assertTrue(fila.enfileirar("a"));
        assertTrue(fila.enfileirar("b"));
        assertTrue(fila.enfileirar("c"));
        assertEquals("a", fila.desenfileirar().ref());
        assertEquals("b", fila.desenfileirar().ref());
        assertEquals("c", fila.desenfileirar().ref());
        assertNull(fila.desenfileirar());
    }

    @Test
    void aceitaPedidoSemIdComRefNulo() {
        PendentesDeResposta fila = new PendentesDeResposta();
        assertTrue(fila.enfileirar(null));
        PendentesDeResposta.Pendencia pendencia = fila.desenfileirar();
        assertNotNull(pendencia);
        assertNull(pendencia.ref());
    }

    @Test
    void recusaOSexagesimoQuintoPedidoPendente() {
        PendentesDeResposta fila = new PendentesDeResposta();
        for (int i = 0; i < PendentesDeResposta.MAXIMO; i++) {
            assertTrue(fila.enfileirar("p" + i));
        }
        assertFalse(fila.enfileirar("excedente"));
        assertEquals(PendentesDeResposta.MAXIMO, fila.tamanho());
        // Uma resposta libera espaço
        fila.desenfileirar();
        assertTrue(fila.enfileirar("novo"));
    }
}
