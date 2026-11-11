package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Autenticação pela ponte: auth.ok, AUTH_INVALIDA com 4001, prazo, AUTH_REQUERIDA e token expirado com 4003. */
class AutenticacaoPonteTest extends CenarioSemBanco {

    @Test
    void authValidoRespondeAuthOkComRefDaRequisicao() throws Exception {
        ClienteNavegador cliente = conectar();
        String token = jwt.emitir(ana, "Ana Souza", Papel.ALUNO).token();

        cliente.enviar(frame("auth", "a1", "{\"token\":\"" + token + "\"}"));

        var resposta = cliente.proximo("auth.ok");
        assertEquals("a1", resposta.get("ref").stringValue());
        assertEquals(1, resposta.get("v").intValue());
        assertNull(resposta.get("id"));
        assertEquals(ana, resposta.get("dados").get("usuarioId").stringValue());
        assertEquals("ALUNO", resposta.get("dados").get("papel").stringValue());
        assertEquals("Ana Souza", resposta.get("dados").get("nome").stringValue());
    }

    @Test
    void tokenInvalidoRecebeAuthInvalidaEFechaCom4001() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar(frame("auth", "a1", "{\"token\":\"nao.e.um.jwt\"}"));

        var erro = cliente.proximo("erro");
        assertEquals("a1", erro.get("ref").stringValue());
        assertEquals("AUTH_INVALIDA", erro.get("dados").get("codigo").stringValue());
        cliente.aguardarFechamentoCom(4001);
    }

    @Test
    void tokenDeOutroSegredoTambemEhAuthInvalida() throws Exception {
        ClienteNavegador cliente = conectar();
        String token = new JwtService("outro-segredo-com-mais-de-32-bytes-0123456789", Duration.ofHours(1))
                .emitir(ana, "Ana", Papel.ALUNO).token();

        cliente.enviar(frame("auth", "a1", "{\"token\":\"" + token + "\"}"));

        assertEquals("AUTH_INVALIDA", cliente.proximo("erro").get("dados").get("codigo").stringValue());
        cliente.aguardarFechamentoCom(4001);
    }

    @Test
    void pingAntesDeAuthRespondePong() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar(frame("ping", "p1", "{}"));

        var pong = cliente.proximo("pong");
        assertEquals("p1", pong.get("ref").stringValue());
    }

    @Test
    void pedidoDeNegocioAntesDeAuthRecebeAuthRequerida() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar(frame("sessao.abrir", "s1", "{\"turmaId\":\"" + turmaId + "\",\"modo\":\"CLASSICA\"}"));

        var erro = cliente.proximo("erro");
        assertEquals("s1", erro.get("ref").stringValue());
        assertEquals(CodigoErro.AUTH_REQUERIDA.name(), erro.get("dados").get("codigo").stringValue());
        // A conexão continua aberta
        cliente.enviar(frame("ping", "p2", "{}"));
        assertEquals("p2", cliente.proximo("pong").get("ref").stringValue());
    }

    @Test
    void authRepetidoEhMensagemInvalidaSemFecharAConexao() throws Exception {
        ClienteNavegador cliente = autenticado(ana, Papel.ALUNO);
        String token = jwt.emitir(ana, "Ana", Papel.ALUNO).token();

        cliente.enviar(frame("auth", "a2", "{\"token\":\"" + token + "\"}"));

        var erro = cliente.proximo("erro");
        assertEquals("a2", erro.get("ref").stringValue());
        assertEquals("MENSAGEM_INVALIDA", erro.get("dados").get("codigo").stringValue());
        cliente.enviar(frame("ping", "p3", "{}"));
        cliente.proximo("pong");
    }

    @Test
    void semAuthNoPrazoFechaCom4001() throws Exception {
        // Ponte com prazo curto: o servidor de teste teria 5 s, e a ponte fecha antes
        try (PonteDeTeste curta = new PonteDeTeste(servidor.porta(), Duration.ofMillis(300), Duration.ofSeconds(60))) {
            ClienteNavegador cliente = new ClienteNavegador(curta.porta());
            try {
                cliente.aguardarFechamentoCom(4001);
            } finally {
                cliente.close();
            }
        }
    }

    @Test
    void tokenExpiradoDuranteAConexaoFechaCom4003() throws Exception {
        // Token de 2 s: o servidor fecha quando ele vence, e a ponte deduz 4003 pelo expiraEm do auth.ok
        JwtService curto = new JwtService(SEGREDO, Duration.ofSeconds(2));
        ClienteNavegador cliente = conectar();
        String token = curto.emitir(ana, "Ana", Papel.ALUNO).token();
        cliente.enviar(frame("auth", "a1", "{\"token\":\"" + token + "\"}"));
        cliente.proximo("auth.ok");

        cliente.aguardarFechamentoCom(4003);
    }
}
