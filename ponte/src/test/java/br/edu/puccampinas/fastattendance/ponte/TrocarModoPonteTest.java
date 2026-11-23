/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code sessao.trocar_modo} pela ponte (#49): resposta {@code sessao.modo_alterado} com {@code ref}, erros
 * com {@code ref}, evento sem {@code ref} aos acompanhantes (também em outra réplica) e, principalmente,
 * que o evento novo não ocupa a vez de uma resposta na fila de pendentes da ponte.
 */
class TrocarModoPonteTest extends CenarioDePonte {

    @Test
    void trocaRespondeModoAlteradoComRefESalaNoFormatoDoProtocolo() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);

        professor.enviar(trocar("t1", sessao, "QRCODE", salaId));

        JsonNode resposta = professor.proximo("sessao.modo_alterado");
        assertEquals("t1", resposta.get("ref").stringValue());
        JsonNode dados = resposta.get("dados");
        assertEquals(sessao.id(), dados.get("sessaoId").stringValue());
        assertEquals("QRCODE", dados.get("modo").stringValue());
        assertEquals(salaId, dados.get("salaId").stringValue());
        assertTrue(dados.get("alteradoEm").longValue() > 0);
        assertEquals(4, dados.size());
    }

    @Test
    void trocaParaModoSemSalaMandaSalaNula() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);

        professor.enviar(trocar("t1", sessao, "SOM", null));

        JsonNode dados = professor.proximo("sessao.modo_alterado").get("dados");
        assertTrue(dados.has("salaId"));
        assertTrue(dados.get("salaId").isNull());
    }

    /**
     * Regressão: a ponte casa as respostas com os pedidos pela ordem. Se o evento novo fosse tratado como
     * resposta, ele chegaria com o ref de outro pedido ou viraria "resposta sem pedido" (ERRO_INTERNO), e a
     * resposta seguinte ficaria com o ref errado.
     */
    @Test
    void eventoDeModoAlteradoNaoOcupaAVezDeUmaResposta() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);
        professor.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.proximo("sessao.estado");

        professor.enviar(trocar("t1", sessao, "SOM", null));

        // Resposta (ref t1) e evento (sem ref), em ordem indefinida; nada mais
        List<JsonNode> recebidos = List.of(professor.proximo(), professor.proximo());
        JsonNode resposta = recebidos.stream().filter(f -> f.get("ref") != null).findFirst().orElseThrow();
        JsonNode evento = recebidos.stream().filter(f -> f.get("ref") == null).findFirst().orElseThrow();
        assertEquals("t1", resposta.get("ref").stringValue());
        assertEquals("sessao.modo_alterado", resposta.get("tipo").stringValue());
        assertEquals("sessao.modo_alterado", evento.get("tipo").stringValue());
        assertEquals("SOM", evento.get("dados").get("modo").stringValue());
        professor.nenhumFrame(500);

        // A fila de pendentes continua alinhada: cada pedido seguinte recebe a própria resposta
        professor.enviar(frame("ping", "p1", "{}"));
        assertEquals("p1", professor.proximo("pong").get("ref").stringValue());
        // Dois pedidos seguidos, válidos no modo SOM: cada resposta com o ref do seu pedido, sem erro
        professor.enviar(frame("sessao.acompanhar", "a2", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.enviar(frame("ping", "p2", "{}"));
        List<JsonNode> depois = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            depois.add(professor.proximo());
        }
        assertTrue(depois.stream().anyMatch(f -> "a2".equals(texto(f, "ref")) && "sessao.estado".equals(texto(f, "tipo"))),
                depois.toString());
        assertTrue(depois.stream().anyMatch(f -> "p2".equals(texto(f, "ref")) && "pong".equals(texto(f, "tipo"))),
                depois.toString());
        assertTrue(depois.stream().noneMatch(f -> "erro".equals(texto(f, "tipo"))), depois.toString());
        assertEquals("SOM", depois.stream().filter(f -> "a2".equals(texto(f, "ref"))).findFirst().orElseThrow()
                .get("dados").get("modo").stringValue());
    }

    @Test
    void presencasRegistradasAntesDaTrocaContinuamNoEstado() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "SOM", null);
        ClienteNavegador aluna = alunoConectado(ana);
        aluna.enviar(frame("checkin", "c1", "{\"sessaoId\":\"" + sessao.id() + "\",\"token\":\""
                + tokenAtual(sessao.segredo()) + "\",\"canal\":\"SOM\",\"dispositivoId\":\"d-ana\"}"));
        aluna.proximo("checkin.ok");

        professor.enviar(trocar("t1", sessao, "CLASSICA", null));
        professor.proximo("sessao.modo_alterado");
        professor.enviar(frame("presenca.marcar", "m1", "{\"sessaoId\":\"" + sessao.id() + "\",\"alunoId\":\""
                + bruno + "\",\"presente\":true}"));
        assertEquals(2, professor.proximo("presenca.atualizada").get("dados").get("totalPresentes").intValue());

        professor.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        JsonNode estado = professor.proximo("sessao.estado").get("dados");
        assertEquals("CLASSICA", estado.get("modo").stringValue());
        assertEquals(2, estado.get("presentes").size());
    }

    @Test
    void depoisDaTrocaOAlunoNoCanalAntigoRecebeModoIncompativel() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "SOM", null);
        professor.enviar(trocar("t1", sessao, "QRCODE", salaId));
        professor.proximo("sessao.modo_alterado");

        ClienteNavegador aluna = alunoConectado(ana);
        aluna.enviar(frame("checkin", "c1", "{\"sessaoId\":\"" + sessao.id() + "\",\"token\":\""
                + tokenAtual(sessao.segredo()) + "\",\"canal\":\"SOM\",\"dispositivoId\":\"d-ana\"}"));

        JsonNode erro = aluna.proximo("erro");
        assertEquals("c1", erro.get("ref").stringValue());
        assertEquals("MODO_INCOMPATIVEL", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void errosDaTrocaChegamComORefDoPedido() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "SOM", null);

        professor.enviar(trocar("sem-sala", sessao, "QRCODE", null));
        assertErro(professor, "sem-sala", "MENSAGEM_INVALIDA");

        professor.enviar(frame("sessao.trocar_modo", "sem-modo", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        assertErro(professor, "sem-modo", "MENSAGEM_INVALIDA");

        professor.enviar(frame("sessao.trocar_modo", "modo-ruim",
                "{\"sessaoId\":\"" + sessao.id() + "\",\"modo\":\"FOTO\"}"));
        assertErro(professor, "modo-ruim", "MENSAGEM_INVALIDA");

        professor.enviar(frame("sessao.trocar_modo", "inexistente",
                "{\"sessaoId\":\"66f1c2a1b2c3d4e5f6a7b8c9\",\"modo\":\"SOM\"}"));
        assertErro(professor, "inexistente", "SESSAO_NAO_ENCONTRADA");

        ClienteNavegador outro = autenticado(new org.bson.types.ObjectId().toHexString(), Papel.PROFESSOR);
        outro.enviar(trocar("outro", sessao, "CLASSICA", null));
        assertErro(outro, "outro", "PERMISSAO_NEGADA");

        ClienteNavegador aluna = alunoConectado(ana);
        aluna.enviar(trocar("aluna", sessao, "CLASSICA", null));
        assertErro(aluna, "aluna", "PERMISSAO_NEGADA");

        professor.enviar(frame("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.proximo("sessao.encerrada");
        professor.enviar(trocar("encerrada", sessao, "CLASSICA", null));
        assertErro(professor, "encerrada", "SESSAO_ENCERRADA");
    }

    @Test
    void trocaAntesDeAutenticarEhAuthRequerida() throws Exception {
        ClienteNavegador cliente = conectar();

        cliente.enviar(frame("sessao.trocar_modo", "t0", "{\"sessaoId\":\"s\",\"modo\":\"SOM\"}"));

        assertErro(cliente, "t0", "AUTH_REQUERIDA");
    }

    @Test
    void trocaFeitaNaReplicaBChegaComoEventoAoProfessorDaReplicaA() throws Exception {
        try (ServidorDeTeste outraReplica = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60));
             PonteDeTeste pontePropria = new PonteDeTeste(outraReplica.porta(), Duration.ofSeconds(5),
                     Duration.ofSeconds(60))) {
            ClienteNavegador professorA = professor();
            Sessao sessao = abrirSessao(professorA, "SOM", null);
            professorA.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
            professorA.proximo("sessao.estado");

            ClienteNavegador professorB = new ClienteNavegador(pontePropria.porta());
            try {
                professorB.enviar("{\"v\":1,\"tipo\":\"auth\",\"id\":\"auth-b\",\"dados\":{\"token\":\""
                        + jwt.emitir(professorId, "Professor", Papel.PROFESSOR).token() + "\"}}");
                professorB.proximo("auth.ok");
                professorB.enviar(trocar("tb", sessao, "QRCODE", salaId));
                assertEquals("tb", professorB.proximo("sessao.modo_alterado").get("ref").stringValue());
            } finally {
                professorB.close();
            }

            JsonNode evento = professorA.proximo("sessao.modo_alterado");
            assertNull(evento.get("ref"));
            assertEquals("QRCODE", evento.get("dados").get("modo").stringValue());
            assertEquals(salaId, evento.get("dados").get("salaId").stringValue());
            professorA.nenhumFrame(500);
        }
    }

    // ---- Apoio ----

    private static String trocar(String id, Sessao sessao, String modo, String sala) {
        String campoSala = sala == null ? "" : ",\"salaId\":\"" + sala + "\"";
        return frame("sessao.trocar_modo", id,
                "{\"sessaoId\":\"" + sessao.id() + "\",\"modo\":\"" + modo + "\"" + campoSala + "}");
    }

    private static void assertErro(ClienteNavegador cliente, String ref, String codigo) {
        JsonNode erro = cliente.proximo("erro");
        assertEquals(ref, erro.get("ref").stringValue());
        assertEquals(codigo, erro.get("dados").get("codigo").stringValue());
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.stringValue();
    }
}
