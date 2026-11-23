/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Painel ao vivo pela ponte: {@code sessao.acompanhar} responde com ref; presença e encerramento chegam ao
 * acompanhante como eventos sem ref. Inclui duas réplicas do servidor de sessão, cada uma atrás de sua ponte.
 */
class PainelPonteTest extends CenarioDePonte {

    @Test
    void acompanharRespondeSessaoEstadoComRef() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);

        professor.enviar(frame("sessao.acompanhar", "ac1", "{\"sessaoId\":\"" + sessao.id() + "\"}"));

        var estado = professor.proximo("sessao.estado");
        assertEquals("ac1", estado.get("ref").stringValue());
        assertEquals(sessao.id(), estado.get("dados").get("sessaoId").stringValue());
        assertEquals(0, estado.get("dados").get("presentes").size());
        assertEquals(2, estado.get("dados").get("totalMatriculados").intValue());
    }

    @Test
    void acompanharSessaoInexistenteRecebeSessaoNaoEncontrada() throws Exception {
        ClienteNavegador professor = professor();

        professor.enviar(frame("sessao.acompanhar", "ac2", "{\"sessaoId\":\"66f1c2a1b2c3d4e5f6a7b8c9\"}"));

        var erro = professor.proximo("erro");
        assertEquals("ac2", erro.get("ref").stringValue());
        assertEquals("SESSAO_NAO_ENCONTRADA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void marcacaoFeitaPeloProfessorChegaAoProprioAcompanhanteComEvento() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);
        professor.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.proximo("sessao.estado");

        professor.enviar(frame("presenca.marcar", "m1", "{\"sessaoId\":\"" + sessao.id() + "\",\"alunoId\":\""
                + ana + "\",\"presente\":true}"));

        // Resposta (com ref) e evento (sem ref) chegam em ordem indefinida: os dois devem aparecer
        var primeiro = professor.proximo();
        var segundo = professor.proximo();
        var resposta = primeiro.get("ref") != null ? primeiro : segundo;
        var evento = primeiro.get("ref") != null ? segundo : primeiro;
        assertEquals("m1", resposta.get("ref").stringValue());
        assertEquals("presenca.atualizada", resposta.get("tipo").stringValue());
        assertNull(evento.get("ref"));
        assertEquals("presenca.atualizada", evento.get("tipo").stringValue());
        assertEquals(ana, evento.get("dados").get("alunoId").stringValue());
    }

    @Test
    void encerramentoChegaAoAcompanhanteComoEventoSemRef() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);
        professor.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.proximo("sessao.estado");

        professor.enviar(frame("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessao.id() + "\"}"));

        var primeiro = professor.proximo();
        var segundo = professor.proximo();
        var resposta = primeiro.get("ref") != null ? primeiro : segundo;
        var evento = primeiro.get("ref") != null ? segundo : primeiro;
        assertEquals("e1", resposta.get("ref").stringValue());
        assertNull(evento.get("ref"));
        assertEquals("sessao.encerrada", evento.get("tipo").stringValue());
    }

    @Test
    void presencaGravadaEmOutraReplicaChegaAoProfessorDaPrimeira() throws Exception {
        // Duas réplicas do servidor de sessão (cada uma atrás da sua ponte) no mesmo MongoDB
        try (ServidorDeTeste outraReplica = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60));
             PonteDeTeste pontePropria = new PonteDeTeste(outraReplica.porta(), Duration.ofSeconds(5),
                     Duration.ofSeconds(60))) {
            ClienteNavegador professor = professor();
            Sessao sessao = abrirSessao(professor, "CLASSICA", null);
            professor.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
            professor.proximo("sessao.estado");

            ClienteNavegadorExterno professorReplicaB = new ClienteNavegadorExterno(pontePropria.porta());
            try {
                professorReplicaB.autenticar(jwt.emitir(professorId, "Professor", Papel.PROFESSOR).token());
                professorReplicaB.cliente().enviar(frame("presenca.marcar", "rb", "{\"sessaoId\":\"" + sessao.id()
                        + "\",\"alunoId\":\"" + bruno + "\",\"presente\":true}"));
                assertEquals("rb", professorReplicaB.cliente().proximo("presenca.atualizada").get("ref").stringValue());
            } finally {
                professorReplicaB.fechar();
            }

            // A réplica A não recebeu a marcação: o evento vem do change stream do MongoDB
            var evento = professor.proximo("presenca.atualizada");
            assertNull(evento.get("ref"));
            assertEquals(bruno, evento.get("dados").get("alunoId").stringValue());
        }
    }

    @Test
    void checkinNaReplicaBChegaAoProfessorNaReplicaA() throws Exception {
        try (ServidorDeTeste outraReplica = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60));
             PonteDeTeste pontePropria = new PonteDeTeste(outraReplica.porta(), Duration.ofSeconds(5),
                     Duration.ofSeconds(60))) {
            ClienteNavegador professor = professor();
            Sessao sessao = abrirSessao(professor, "SOM", null);
            professor.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
            professor.proximo("sessao.estado");

            ClienteNavegadorExterno alunoReplicaB = new ClienteNavegadorExterno(pontePropria.porta());
            try {
                alunoReplicaB.autenticar(jwt.emitir(ana, "Ana", Papel.ALUNO).token());
                alunoReplicaB.cliente().enviar(frame("checkin", "cb", "{\"sessaoId\":\"" + sessao.id()
                        + "\",\"token\":\"" + tokenAtual(sessao.segredo()) + "\",\"canal\":\"SOM\",\"dispositivoId\":\"d\"}"));
                alunoReplicaB.cliente().proximo("checkin.ok");
            } finally {
                alunoReplicaB.fechar();
            }

            var evento = professor.proximo("presenca.atualizada");
            assertNull(evento.get("ref"));
            assertEquals(ana, evento.get("dados").get("alunoId").stringValue());
            assertEquals("SOM", evento.get("dados").get("origem").stringValue());
        }
    }

    /** Cliente numa ponte que não é a da fixture, para os testes de duas réplicas. */
    private static final class ClienteNavegadorExterno {
        private final ClienteNavegador cliente;

        ClienteNavegadorExterno(int porta) throws Exception {
            cliente = new ClienteNavegador(porta);
        }

        void autenticar(String token) throws Exception {
            cliente.enviar("{\"v\":1,\"tipo\":\"auth\",\"id\":\"auth-ext\",\"dados\":{\"token\":\"" + token + "\"}}");
            cliente.proximo("auth.ok");
        }

        ClienteNavegador cliente() {
            return cliente;
        }

        void fechar() {
            cliente.close();
        }
    }
}
