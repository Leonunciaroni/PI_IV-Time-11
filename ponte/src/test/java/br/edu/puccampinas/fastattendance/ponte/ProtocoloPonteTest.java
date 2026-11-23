/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validação do frame pela ponte (docs/protocolo-v1.md, seções 1, 2, 4 e 7): cada caso recusado recebe o
 * erro do protocolo, com ou sem {@code ref} conforme o {@code id} pode ser lido, e a conexão segue aberta
 * quando o protocolo não manda fechar.
 */
class ProtocoloPonteTest extends CenarioSemBanco {

    @Test
    void jsonMalformadoRecebeMensagemInvalidaSemRef() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar("{ isto não é json");

        var erro = cliente.proximo("erro");
        assertNull(erro.get("ref"));
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
        cliente.enviar(frame("ping", "p1", "{}"));
        assertEquals("p1", cliente.proximo("pong").get("ref").stringValue());
    }

    @Test
    void versaoErradaRecebeVersaoNaoSuportadaEFechaCom4002() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar("{\"v\":2,\"tipo\":\"ping\",\"id\":\"v2\",\"dados\":{}}");

        var erro = cliente.proximo("erro");
        assertEquals("v2", erro.get("ref").stringValue());
        assertEquals("VERSAO_NAO_SUPORTADA", erro.get("dados").get("codigo").stringValue());
        cliente.aguardarFechamentoCom(4002);
    }

    @Test
    void idComMaisDe64CaracteresRecebeMensagemInvalidaSemRef() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar(frame("ping", "x".repeat(65), "{}"));

        var erro = cliente.proximo("erro");
        assertNull(erro.get("ref"));
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void tipoDesconhecidoRecebeMensagemInvalidaComRef() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar(frame("xpto.nada", "t1", "{}"));

        var erro = cliente.proximo("erro");
        assertEquals("t1", erro.get("ref").stringValue());
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void tipoQueOClienteNaoEnviaRecebeMensagemInvalida() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar(frame("pong", "t2", "{}"));

        assertEquals("MENSAGEM_INVALIDA", cliente.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void dadosQueNaoEhObjetoRecebeMensagemInvalida() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar("{\"v\":1,\"tipo\":\"ping\",\"id\":\"d1\",\"dados\":[1,2]}");

        var erro = cliente.proximo("erro");
        assertEquals("d1", erro.get("ref").stringValue());
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void frameBinarioRecebeMensagemInvalidaSemRef() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviarBinario(new byte[]{1, 2, 3});

        var erro = cliente.proximo("erro");
        assertNull(erro.get("ref"));
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void modoDesconhecidoEhMensagemInvalidaComRef() throws Exception {
        ClienteNavegador professor = professor();

        professor.enviar(frame("sessao.abrir", "m1", "{\"turmaId\":\"" + turmaId + "\",\"modo\":\"FOTO\"}"));

        var erro = professor.proximo("erro");
        assertEquals("m1", erro.get("ref").stringValue());
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void canalDesconhecidoEhMensagemInvalida() throws Exception {
        ClienteNavegador aluno = alunoConectado(ana);
        String sessaoId = "66f1c2a1b2c3d4e5f6a7b8c9";

        aluno.enviar(frame("checkin", "c1", "{\"sessaoId\":\"" + sessaoId + "\",\"token\":\"123456\","
                + "\"canal\":\"NFC\",\"dispositivoId\":\"d\"}"));

        var erro = aluno.proximo("erro");
        assertEquals("c1", erro.get("ref").stringValue());
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void presenteQueNaoEhBooleanoRecebeMensagemInvalidaComRef() throws Exception {
        ClienteNavegador professor = professor();

        // Recusado pela ponte antes de chegar ao servidor: a sessão nem precisa existir
        professor.enviar(frame("presenca.marcar", "pm", "{\"sessaoId\":\"66f1c2a1b2c3d4e5f6a7b8c9\",\"alunoId\":\""
                + ana + "\",\"presente\":\"sim\"}"));

        var erro = professor.proximo("erro");
        assertEquals("pm", erro.get("ref").stringValue());
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void frameAcimaDe16KiBFechaAConexao() throws Exception {
        ClienteNavegador cliente = conectar();
        String grande = "{\"v\":1,\"tipo\":\"ping\",\"id\":\"g\",\"dados\":{\"x\":\"" + "a".repeat(17 * 1024) + "\"}}";

        cliente.enviar(grande);

        // O WebSocket recusa o frame antes da ponte (limite de 16 KiB); o fechamento é da biblioteca
        assertTrue(cliente.aguardarFechamento(5_000) != 0);
    }

    @Test
    void excessoDeMensagensRecebeLimiteExcedidoEFechaCom4008() throws Exception {
        ClienteNavegador cliente = autenticado(ana, Papel.ALUNO);
        // O auth já contou uma mensagem: 59 pings completam 60; as seguintes são recusadas pela ponte
        for (int i = 0; i < 59; i++) {
            cliente.enviar(frame("ping", "ok" + i, "{}"));
        }
        for (int i = 0; i < 5; i++) {
            cliente.enviar(frame("ping", "ex" + i, "{}"));
        }
        // Os pongs e os erros vêm por threads diferentes: só a ordem dos erros é garantida
        List<String> limites = new ArrayList<>();
        while (limites.size() < 5) {
            var frame = cliente.proximo();
            if ("erro".equals(frame.get("tipo").stringValue())) {
                assertEquals("LIMITE_EXCEDIDO", frame.get("dados").get("codigo").stringValue());
                limites.add(frame.get("ref").stringValue());
            }
        }
        assertEquals(List.of("ex0", "ex1", "ex2", "ex3", "ex4"), limites);
        cliente.aguardarFechamentoCom(4008);
    }
}
