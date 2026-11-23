/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Chamada pela ponte: sessão, chamada clássica e encerramento, com os nomes e as respostas do protocolo v1. */
class ChamadaPonteTest extends CenarioDePonte {

    @Test
    void sessaoAbrirRespondeSessaoAbertaComTotpERef() throws Exception {
        ClienteNavegador professor = professor();

        professor.enviar(frame("sessao.abrir", "ab1", "{\"turmaId\":\"" + turmaId + "\",\"modo\":\"CLASSICA\"}"));

        var resposta = professor.proximo("sessao.aberta");
        assertEquals("ab1", resposta.get("ref").stringValue());
        var dados = resposta.get("dados");
        assertEquals(turmaId, dados.get("turmaId").stringValue());
        assertEquals("CLASSICA", dados.get("modo").stringValue());
        assertEquals(32, dados.get("totp").get("segredo").stringValue().length());
        assertEquals(6, dados.get("totp").get("digitos").intValue());
        assertEquals(15, dados.get("totp").get("periodoSegundos").intValue());
    }

    @Test
    void alunoNaoAbreSessaoEPodeTentarDeNovo() throws Exception {
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(frame("sessao.abrir", "ab2", "{\"turmaId\":\"" + turmaId + "\",\"modo\":\"CLASSICA\"}"));

        var erro = aluno.proximo("erro");
        assertEquals("ab2", erro.get("ref").stringValue());
        assertEquals("PERMISSAO_NEGADA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void presencaMarcadaERespondidaComPresencaAtualizadaComRef() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);

        professor.enviar(frame("presenca.marcar", "m1", "{\"sessaoId\":\"" + sessao.id() + "\",\"alunoId\":\""
                + ana + "\",\"presente\":true}"));

        var resposta = professor.proximo("presenca.atualizada");
        assertEquals("m1", resposta.get("ref").stringValue());
        assertEquals(ana, resposta.get("dados").get("alunoId").stringValue());
        assertTrue(resposta.get("dados").get("presente").booleanValue());
        assertEquals("MANUAL", resposta.get("dados").get("origem").stringValue());
        assertEquals(1, resposta.get("dados").get("totalPresentes").intValue());
        assertEquals(2, resposta.get("dados").get("totalMatriculados").intValue());
    }

    @Test
    void chamadaEmLoteRespondeLoteOk() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);

        professor.enviar(frame("presenca.marcar_lote", "l1", "{\"sessaoId\":\"" + sessao.id()
                + "\",\"presente\":true,\"excecoes\":[\"" + bruno + "\"]}"));

        var resposta = professor.proximo("presenca.lote_ok");
        assertEquals("l1", resposta.get("ref").stringValue());
        assertEquals(1, resposta.get("dados").get("totalPresentes").intValue());
        assertEquals(2, resposta.get("dados").get("totalMatriculados").intValue());
    }

    @Test
    void chamadaEmLoteSemExcecoesMarcaTodosERespondeLoteOk() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);

        professor.enviar(frame("presenca.marcar_lote", "l2", "{\"sessaoId\":\"" + sessao.id()
                + "\",\"presente\":true}"));

        var resposta = professor.proximo("presenca.lote_ok");
        assertEquals("l2", resposta.get("ref").stringValue());
        assertEquals(2, resposta.get("dados").get("totalPresentes").intValue());
        assertEquals(2, resposta.get("dados").get("totalMatriculados").intValue());
    }

    @Test
    void encerrarRespondeSessaoEncerradaComRefETotalDePresentes() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);
        professor.enviar(frame("presenca.marcar", "m1", "{\"sessaoId\":\"" + sessao.id() + "\",\"alunoId\":\""
                + ana + "\",\"presente\":true}"));
        professor.proximo("presenca.atualizada");

        professor.enviar(frame("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessao.id() + "\"}"));

        var resposta = professor.proximo("sessao.encerrada");
        assertEquals("e1", resposta.get("ref").stringValue());
        assertEquals("PROFESSOR", resposta.get("dados").get("motivo").stringValue());
        assertEquals(1, resposta.get("dados").get("totalPresentes").intValue());
    }

    @Test
    void depoisDeEncerrarNenhumRegistroEAceito() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "CLASSICA", null);
        professor.enviar(frame("sessao.encerrar", "e1", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.proximo("sessao.encerrada");

        professor.enviar(frame("presenca.marcar", "m2", "{\"sessaoId\":\"" + sessao.id() + "\",\"alunoId\":\""
                + ana + "\",\"presente\":true}"));

        var erro = professor.proximo("erro");
        assertEquals("m2", erro.get("ref").stringValue());
        assertEquals("SESSAO_ENCERRADA", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void segundaSessaoNaMesmaTurmaEhSessaoJaAberta() throws Exception {
        ClienteNavegador professor = professor();
        abrirSessao(professor, "CLASSICA", null);

        professor.enviar(frame("sessao.abrir", "ab3", "{\"turmaId\":\"" + turmaId + "\",\"modo\":\"CLASSICA\"}"));

        assertEquals("SESSAO_JA_ABERTA", professor.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void professorDeOutraTurmaRecebePermissaoNegadaSemRevelarATurma() throws Exception {
        ClienteNavegador outro = autenticado(new org.bson.types.ObjectId().toHexString(), Papel.PROFESSOR);

        outro.enviar(frame("sessao.abrir", "ab4", "{\"turmaId\":\"" + turmaId + "\",\"modo\":\"CLASSICA\"}"));

        assertEquals("PERMISSAO_NEGADA", outro.proximo("erro").get("dados").get("codigo").stringValue());
    }
}
