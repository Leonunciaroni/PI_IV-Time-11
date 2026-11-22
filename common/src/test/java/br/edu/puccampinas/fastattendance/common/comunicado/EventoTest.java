package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A ponte trata como evento (sem {@code ref}, sem ocupar a vez de uma resposta) todo comunicado que
 * implementa {@link Evento}. Um evento sem a marca seria tratado como resposta e embaralharia os
 * {@code ref} das respostas seguintes; uma resposta com a marca nunca chegaria a quem pediu.
 */
class EventoTest {

    @Test
    void todosOsEventosTemAMarca() {
        for (Class<?> evento : List.of(EventoDePresencaAtualizada.class, EventoDeSessaoEncerrada.class,
                EventoDeModoAlterado.class)) {
            assertTrue(Evento.class.isAssignableFrom(evento), evento.getSimpleName() + " sem a marca Evento");
        }
    }

    @Test
    void respostasNaoTemAMarca() {
        for (Class<?> resposta : List.of(RespostaDeTrocarModo.class, RespostaDeEncerrarSessao.class,
                RespostaDeMarcarPresenca.class, RespostaDeAcompanharSessao.class, RespostaDeCheckin.class,
                Erro.class)) {
            assertFalse(Evento.class.isAssignableFrom(resposta), resposta.getSimpleName() + " marcada como Evento");
        }
    }

    @Test
    void eventoDeModoAlteradoAceitaSalaNula() {
        EventoDeModoAlterado evento = new EventoDeModoAlterado("s", ModoChamada.SOM, null, 10L);
        assertNull(evento.getSalaId());
        assertEquals(10L, evento.getAlteradoEm());
    }
}
