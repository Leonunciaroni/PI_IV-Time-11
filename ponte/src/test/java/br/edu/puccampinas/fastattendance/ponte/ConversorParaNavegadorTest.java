package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.comunicado.ConfiguracaoTotp;
import br.edu.puccampinas.fastattendance.common.comunicado.Erro;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDePresencaAtualizada;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDePong;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/** Formato dos frames de saída: envelope de docs/protocolo-v1.md, seção 2, e {@code ref} só em resposta. */
class ConversorParaNavegadorTest {

    private final Json json = new Json();
    private final ConversorParaNavegador conversor = new ConversorParaNavegador(json);

    private JsonNode ler(String frame) {
        return json.ler(frame);
    }

    @Test
    void respostaLevaRefEEnvelopeCompleto() {
        JsonNode frame = ler(conversor.paraFrame(new RespostaDePong(), "p1"));
        assertEquals(1, frame.get("v").intValue());
        assertEquals("pong", frame.get("tipo").stringValue());
        assertEquals("p1", frame.get("ref").stringValue());
        assertTrue(frame.get("ts").longValue() > 0);
        assertTrue(frame.get("dados").isObject());
        assertNull(frame.get("id"));
    }

    @Test
    void eventoNaoTemRef() {
        EventoDePresencaAtualizada evento = new EventoDePresencaAtualizada("s", "a", "Ana", true,
                OrigemPresenca.QRCODE, 10L, 3, 5);
        JsonNode frame = ler(conversor.paraFrame(evento, null));
        assertEquals("presenca.atualizada", frame.get("tipo").stringValue());
        assertNull(frame.get("ref"));
        assertEquals("QRCODE", frame.get("dados").get("origem").stringValue());
        assertEquals(5, frame.get("dados").get("totalMatriculados").intValue());
    }

    @Test
    void erroTemCodigoEMensagem() {
        JsonNode frame = ler(conversor.paraFrame(new Erro(CodigoErro.FORA_DO_RAIO, "longe"), "c1"));
        assertEquals("erro", frame.get("tipo").stringValue());
        assertEquals("c1", frame.get("ref").stringValue());
        assertEquals("FORA_DO_RAIO", frame.get("dados").get("codigo").stringValue());
        assertEquals("longe", frame.get("dados").get("mensagem").stringValue());
    }

    @Test
    void erroSemRefOmiteOCampo() {
        JsonNode frame = ler(conversor.paraFrame(new Erro(CodigoErro.MENSAGEM_INVALIDA, "x"), null));
        assertNull(frame.get("ref"));
    }

    @Test
    void autenticacaoEAbrirSessaoUsamOsNomesDoProtocolo() {
        JsonNode auth = ler(conversor.paraFrame(
                new RespostaDeAutenticacao("u1", "Ana", Papel.ALUNO, 1234L), "a1"));
        assertEquals("auth.ok", auth.get("tipo").stringValue());
        assertEquals("ALUNO", auth.get("dados").get("papel").stringValue());
        assertEquals(1234L, auth.get("dados").get("expiraEm").longValue());

        ConfiguracaoTotp totp = new ConfiguracaoTotp("JBSWY3DPEHPK3PXP", "HmacSHA256", 6, 15, 0);
        JsonNode aberta = ler(conversor.paraFrame(
                new RespostaDeAbrirSessao("s1", "t1", ModoChamada.SOM, 99L, totp), "s1"));
        assertEquals("sessao.aberta", aberta.get("tipo").stringValue());
        JsonNode dados = aberta.get("dados");
        assertEquals("SOM", dados.get("modo").stringValue());
        assertEquals("JBSWY3DPEHPK3PXP", dados.get("totp").get("segredo").stringValue());
        assertEquals(15, dados.get("totp").get("periodoSegundos").intValue());
    }
}
