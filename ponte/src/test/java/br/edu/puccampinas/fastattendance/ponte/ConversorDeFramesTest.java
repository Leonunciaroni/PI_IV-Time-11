/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDePing;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeTrocarModo;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Validação dos frames do navegador, sem servidor: cada regra recusada pela ponte e o id que volta no erro. */
class ConversorDeFramesTest {

    private final ConversorDeFrames conversor = new ConversorDeFrames(new Json());

    private FalhaDeProtocolo falha(String texto, boolean autenticado) {
        FalhaDeProtocolo erro = assertThrows(FalhaDeProtocolo.class, () -> conversor.analisar(texto, autenticado));
        return erro;
    }

    @Test
    void jsonMalformadoNaoTemRef() {
        FalhaDeProtocolo erro = falha("{ isto não é json", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertNull(erro.ref());
    }

    @Test
    void mensagemQueNaoEhObjetoNaoTemRef() {
        assertNull(falha("[1,2,3]", true).ref());
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, falha("\"texto\"", true).codigo());
    }

    @Test
    void idComMaisDe64CaracteresNaoTemRef() {
        String id = "x".repeat(65);
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"ping\",\"id\":\"" + id + "\"}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertNull(erro.ref());
    }

    @Test
    void idComExatos64CaracteresEhAceito() {
        String id = "x".repeat(64);
        ConversorDeFrames.Recebido recebido = conversor.analisar(
                "{\"v\":1,\"tipo\":\"ping\",\"id\":\"" + id + "\"}", false);
        assertEquals(id, recebido.id());
    }

    @Test
    void versaoAusenteOuNaoInteiraEhMensagemInvalida() {
        FalhaDeProtocolo erro = falha("{\"tipo\":\"ping\",\"id\":\"a1\"}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertEquals("a1", erro.ref());
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, falha("{\"v\":\"1\",\"tipo\":\"ping\"}", true).codigo());
    }

    @Test
    void versaoDiferenteDeUmFechaComVersaoNaoSuportada() {
        FalhaDeProtocolo erro = falha("{\"v\":2,\"tipo\":\"ping\",\"id\":\"a1\"}", true);
        assertEquals(CodigoErro.VERSAO_NAO_SUPORTADA, erro.codigo());
        assertEquals("a1", erro.ref());
        assertEquals(CodigoFechamento.VERSAO_NAO_SUPORTADA, erro.fechamento());
    }

    @Test
    void tipoDesconhecidoOuDeRespostaEhMensagemInvalida() {
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, falha("{\"v\":1,\"tipo\":\"xpto\",\"id\":\"a\"}", true).codigo());
        FalhaDeProtocolo pong = falha("{\"v\":1,\"tipo\":\"pong\",\"id\":\"a\"}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, pong.codigo());
        assertEquals("a", pong.ref());
    }

    @Test
    void dadosQueNaoEhObjetoEhMensagemInvalida() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"ping\",\"id\":\"a\",\"dados\":[1]}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertEquals("a", erro.ref());
    }

    @Test
    void dadosAusenteOuNuloEhObjetoVazio() {
        assertInstanceOf(PedidoDePing.class,
                conversor.analisar("{\"v\":1,\"tipo\":\"ping\",\"id\":\"a\"}", false).comunicado());
        assertInstanceOf(PedidoDePing.class,
                conversor.analisar("{\"v\":1,\"tipo\":\"ping\",\"id\":\"a\",\"dados\":null}", false).comunicado());
    }

    @Test
    void presenteNaoBooleanoEhMensagemInvalidaComRef() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"presenca.marcar\",\"id\":\"m1\","
                + "\"dados\":{\"sessaoId\":\"s\",\"alunoId\":\"a\",\"presente\":\"sim\"}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertEquals("m1", erro.ref());
        assertThrows(FalhaDeProtocolo.class, () -> conversor.analisar(
                "{\"v\":1,\"tipo\":\"presenca.marcar\",\"id\":\"m1\",\"dados\":{\"sessaoId\":\"s\"}}", true));
    }

    @Test
    void modoDesconhecidoEhMensagemInvalida() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"sessao.abrir\",\"id\":\"s1\","
                + "\"dados\":{\"turmaId\":\"t\",\"modo\":\"FOTO\"}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertEquals("s1", erro.ref());
    }

    @Test
    void modoConhecidoVaiAoServidor() {
        ConversorDeFrames.Recebido recebido = conversor.analisar("{\"v\":1,\"tipo\":\"sessao.abrir\",\"id\":\"s1\","
                + "\"dados\":{\"turmaId\":\"t\",\"modo\":\"QRCODE\",\"salaId\":\"sala\"}}", true);
        PedidoDeAbrirSessao pedido = (PedidoDeAbrirSessao) recebido.comunicado();
        assertEquals(ModoChamada.QRCODE, pedido.getModo());
        assertEquals("sala", pedido.getSalaId());
    }

    @Test
    void canalDesconhecidoEhMensagemInvalidaMasMANUALVaiAoServidorQueRecusaORamo() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"checkin\",\"id\":\"c\",\"dados\":{\"canal\":\"NFC\"}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        // Canal conhecido mas sem check-in: a regra MODO_INCOMPATIVEL é do servidor, então a ponte encaminha
        ConversorDeFrames.Recebido recebido = conversor.analisar(
                "{\"v\":1,\"tipo\":\"checkin\",\"id\":\"c\",\"dados\":{\"sessaoId\":\"s\",\"canal\":\"MANUAL\"}}", true);
        PedidoDeCheckin pedido = (PedidoDeCheckin) recebido.comunicado();
        assertEquals(OrigemPresenca.MANUAL, pedido.getCanal());
    }

    @Test
    void coordenadaQueNaoEhNumeroEhMensagemInvalida() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"checkin\",\"id\":\"c\",\"dados\":"
                + "{\"sessaoId\":\"s\",\"canal\":\"QRCODE\",\"latitude\":\"-22\"}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertEquals("c", erro.ref());
    }

    @Test
    void pedidoDeNegocioAntesDeAuthEhAuthRequerida() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"sessao.abrir\",\"id\":\"s1\",\"dados\":{}}", false);
        assertEquals(CodigoErro.AUTH_REQUERIDA, erro.codigo());
        assertEquals("s1", erro.ref());
        // ping e auth passam antes da autenticação
        assertEquals(TipoMensagem.PING, conversor.analisar("{\"v\":1,\"tipo\":\"ping\"}", false).tipo());
        assertEquals(TipoMensagem.AUTH, conversor.analisar("{\"v\":1,\"tipo\":\"auth\",\"dados\":{\"token\":\"t\"}}",
                false).tipo());
    }

    @Test
    void marcarPresencaTraduzCamposDoPedido() {
        ConversorDeFrames.Recebido recebido = conversor.analisar("{\"v\":1,\"tipo\":\"presenca.marcar\",\"id\":\"m\","
                + "\"dados\":{\"sessaoId\":\"s\",\"alunoId\":\"a\",\"presente\":false}}", true);
        PedidoDeMarcarPresenca pedido = (PedidoDeMarcarPresenca) recebido.comunicado();
        assertEquals("s", pedido.getSessaoId());
        assertEquals("a", pedido.getAlunoId());
        assertFalse(pedido.isPresente());
        assertEquals("m", recebido.id());
    }

    @Test
    void trocarModoTraduzCamposDoPedido() {
        ConversorDeFrames.Recebido recebido = conversor.analisar("{\"v\":1,\"tipo\":\"sessao.trocar_modo\",\"id\":\"t1\","
                + "\"dados\":{\"sessaoId\":\"s\",\"modo\":\"QRCODE\",\"salaId\":\"sala\"}}", true);
        assertEquals(TipoMensagem.SESSAO_TROCAR_MODO, recebido.tipo());
        assertEquals("t1", recebido.id());
        PedidoDeTrocarModo pedido = (PedidoDeTrocarModo) recebido.comunicado();
        assertEquals("s", pedido.getSessaoId());
        assertEquals(ModoChamada.QRCODE, pedido.getModo());
        assertEquals("sala", pedido.getSalaId());
    }

    @Test
    void trocarModoSemSalaOuComSalaNulaVaiAoServidorSemSala() {
        for (String dados : new String[]{"{\"sessaoId\":\"s\",\"modo\":\"SOM\"}",
                "{\"sessaoId\":\"s\",\"modo\":\"SOM\",\"salaId\":null}"}) {
            PedidoDeTrocarModo pedido = (PedidoDeTrocarModo) conversor.analisar(
                    "{\"v\":1,\"tipo\":\"sessao.trocar_modo\",\"id\":\"t\",\"dados\":" + dados + "}", true).comunicado();
            assertNull(pedido.getSalaId(), dados);
            assertEquals(ModoChamada.SOM, pedido.getModo(), dados);
        }
    }

    @Test
    void trocarModoComCamposAusentesVaiAoServidorQueRespondeMensagemInvalida() {
        // Campos ausentes não são erro da ponte: o servidor confere a ordem das regras (papel antes dos campos)
        PedidoDeTrocarModo pedido = (PedidoDeTrocarModo) conversor.analisar(
                "{\"v\":1,\"tipo\":\"sessao.trocar_modo\",\"id\":\"t\",\"dados\":{}}", true).comunicado();
        assertNull(pedido.getSessaoId());
        assertNull(pedido.getModo());
    }

    @Test
    void trocarModoComModoDesconhecidoOuCampoDeTipoErradoEhMensagemInvalidaComRef() {
        FalhaDeProtocolo modo = falha("{\"v\":1,\"tipo\":\"sessao.trocar_modo\",\"id\":\"t2\","
                + "\"dados\":{\"sessaoId\":\"s\",\"modo\":\"FOTO\"}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, modo.codigo());
        assertEquals("t2", modo.ref());

        FalhaDeProtocolo sala = falha("{\"v\":1,\"tipo\":\"sessao.trocar_modo\",\"id\":\"t3\","
                + "\"dados\":{\"sessaoId\":\"s\",\"modo\":\"QRCODE\",\"salaId\":42}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, sala.codigo());
        assertEquals("t3", sala.ref());

        FalhaDeProtocolo sessao = falha("{\"v\":1,\"tipo\":\"sessao.trocar_modo\",\"id\":\"t4\","
                + "\"dados\":{\"sessaoId\":[1],\"modo\":\"SOM\"}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, sessao.codigo());
    }

    @Test
    void trocarModoAntesDeAuthEhAuthRequerida() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"sessao.trocar_modo\",\"id\":\"t5\","
                + "\"dados\":{\"sessaoId\":\"s\",\"modo\":\"SOM\"}}", false);
        assertEquals(CodigoErro.AUTH_REQUERIDA, erro.codigo());
        assertEquals("t5", erro.ref());
    }

    @Test
    void modoAlteradoEhTipoDoServidorQueOClienteNaoPodeEnviar() {
        FalhaDeProtocolo erro = falha("{\"v\":1,\"tipo\":\"sessao.modo_alterado\",\"id\":\"x\","
                + "\"dados\":{\"sessaoId\":\"s\",\"modo\":\"SOM\"}}", true);
        assertEquals(CodigoErro.MENSAGEM_INVALIDA, erro.codigo());
        assertEquals("x", erro.ref());
    }

    @Test
    void idLegivelDoTextoMesmoQuandoOFrameEhInvalido() {
        assertEquals("zz", conversor.idSeLegivel("{\"v\":9,\"id\":\"zz\"}"));
        assertNull(conversor.idSeLegivel("nao json"));
    }
}
