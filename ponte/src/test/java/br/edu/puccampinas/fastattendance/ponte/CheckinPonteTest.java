package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.sessao.totp.ValidadorTotp;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code checkin} pela ponte nos canais QR Code e SOM, com as regras antifraude do servidor (docs/protocolo-v1.md,
 * seções 4.8 e 7). Os erros voltam com o código do protocolo e o {@code ref} do pedido.
 */
class CheckinPonteTest extends CenarioDePonte {

    /** 0,0001 grau de latitude (cerca de 11 m) e 0,001 grau (cerca de 111 m). */
    private static final double PERTO_LAT = SALA_LAT + 0.0001;
    private static final double LONGE_LAT = SALA_LAT + 0.001;

    private String checkinQr(Sessao sessao, String token, String alunoId, String dispositivo, double lat, double lon) {
        // alunoId identifica quem está fazendo o check-in na leitura do teste; o servidor usa o JWT da conexão
        return frame("checkin", id("ck"), "{\"sessaoId\":\"" + sessao.id() + "\",\"token\":\"" + token
                + "\",\"canal\":\"QRCODE\",\"dispositivoId\":\"" + dispositivo + "\",\"latitude\":" + lat
                + ",\"longitude\":" + lon + ",\"precisaoMetros\":10.0}");
    }

    private String checkinSom(Sessao sessao, String token, String dispositivo) {
        return frame("checkin", id("ck"), "{\"sessaoId\":\"" + sessao.id() + "\",\"token\":\"" + token
                + "\",\"canal\":\"SOM\",\"dispositivoId\":\"" + dispositivo + "\"}");
    }

    @Test
    void checkinQrDentroDoRaioRegistraERespondeCheckinOk() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador aluno = alunoConectado(ana);

        String id = id("ck");
        aluno.enviar(frame("checkin", id, "{\"sessaoId\":\"" + sessao.id() + "\",\"token\":\""
                + tokenAtual(sessao.segredo()) + "\",\"canal\":\"QRCODE\",\"dispositivoId\":\"disp-ana\","
                + "\"latitude\":" + PERTO_LAT + ",\"longitude\":" + SALA_LON + ",\"precisaoMetros\":10.0}"));

        var resposta = aluno.proximo("checkin.ok");
        assertEquals(id, resposta.get("ref").stringValue());
        assertEquals(sessao.id(), resposta.get("dados").get("sessaoId").stringValue());
        assertTrue(resposta.get("dados").get("registradoEm").longValue() > 0);
        assertEquals(1, presencasDoAluno(ana));
    }

    @Test
    void presencaRegistradaPeloCheckinChegaAoProfessorComoEvento() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        professor.enviar(frame("sessao.acompanhar", "ac", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.proximo("sessao.estado");
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), ana, "disp-ana", PERTO_LAT, SALA_LON));
        aluno.proximo("checkin.ok");

        var evento = professor.proximo("presenca.atualizada");
        assertNull(evento.get("ref"), "evento não tem ref");
        assertEquals(ana, evento.get("dados").get("alunoId").stringValue());
        assertEquals("QRCODE", evento.get("dados").get("origem").stringValue());
    }

    @Test
    void foraDoRaioRecebeForaDoRaioComRef() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), ana, "disp-ana", LONGE_LAT, SALA_LON));

        var erro = aluno.proximo("erro");
        assertEquals("FORA_DO_RAIO", erro.get("dados").get("codigo").stringValue());
        assertNotNull(erro.get("ref"));
        assertEquals(0, presencasDoAluno(ana));
    }

    @Test
    void tokenDeOutraSessaoRecebeTokenInvalido() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador aluno = alunoConectado(ana);
        String token = ValidadorTotp.gerar("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", instanteAgora());

        aluno.enviar(checkinQr(sessao, token, ana, "disp-ana", SALA_LAT, SALA_LON));

        assertEquals("TOKEN_INVALIDO", aluno.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void tokenDeJanelaJaExpiradaRecebeTokenExpirado() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador aluno = alunoConectado(ana);
        // 45 s atrás: 3 janelas de 15 s, dentro da faixa de token expirado (até 75 s)
        String token = ValidadorTotp.gerar(sessao.segredo(), instanteAgora() - 45);

        aluno.enviar(checkinQr(sessao, token, ana, "disp-ana", SALA_LAT, SALA_LON));

        assertEquals("TOKEN_EXPIRADO", aluno.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void presencaDuplicadaRecebePresencaDuplicada() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador aluno = alunoConectado(ana);
        aluno.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), ana, "disp-ana", SALA_LAT, SALA_LON));
        aluno.proximo("checkin.ok");

        aluno.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), ana, "disp-ana", SALA_LAT, SALA_LON));

        assertEquals("PRESENCA_DUPLICADA", aluno.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void dispositivoJaUsadoPorOutroAlunoRecebeDispositivoJaUtilizado() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        alunoConectado(ana).enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), ana, "mesmo", SALA_LAT, SALA_LON));
        ClienteNavegador clienteBruno = alunoConectado(bruno);

        clienteBruno.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), bruno, "mesmo", SALA_LAT, SALA_LON));

        assertEquals("DISPOSITIVO_JA_UTILIZADO", clienteBruno.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void alunoDeOutraTurmaRecebeAlunoNaoMatriculado() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador outro = alunoConectado(alunoDeOutraTurma);

        outro.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), alunoDeOutraTurma, "d", SALA_LAT, SALA_LON));

        assertEquals("ALUNO_NAO_MATRICULADO", outro.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void canalManualRecebeModoIncompativel() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(frame("checkin", "cm", "{\"sessaoId\":\"" + sessao.id() + "\",\"token\":\""
                + tokenAtual(sessao.segredo()) + "\",\"canal\":\"MANUAL\",\"dispositivoId\":\"d\"}"));

        var erro = aluno.proximo("erro");
        assertEquals("cm", erro.get("ref").stringValue());
        assertEquals("MODO_INCOMPATIVEL", erro.get("dados").get("codigo").stringValue());
    }

    @Test
    void qrcodeSemCoordenadasRecebeMensagemInvalida() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(frame("checkin", "sc", "{\"sessaoId\":\"" + sessao.id() + "\",\"token\":\""
                + tokenAtual(sessao.segredo()) + "\",\"canal\":\"QRCODE\",\"dispositivoId\":\"d\"}"));

        assertEquals("MENSAGEM_INVALIDA", aluno.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void checkinSomRegistraPresencaSemCoordenadas() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "SOM", null);
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(checkinSom(sessao, tokenAtual(sessao.segredo()), "disp-som"));

        var resposta = aluno.proximo("checkin.ok");
        assertEquals(sessao.id(), resposta.get("dados").get("sessaoId").stringValue());
        Document gravada = MongoDeTeste.banco().getCollection("presencas").find(eq("alunoId", ana)).first();
        assertNotNull(gravada);
        assertEquals("SOM", gravada.getString("origem"));
    }

    @Test
    void canalQrcodeEmSessaoSomRecebeModoIncompativel() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "SOM", null);
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), ana, "d", SALA_LAT, SALA_LON));

        assertEquals("MODO_INCOMPATIVEL", aluno.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void sessaoEncerradaRecebeSessaoEncerrada() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);
        professor.enviar(frame("sessao.encerrar", "e", "{\"sessaoId\":\"" + sessao.id() + "\"}"));
        professor.proximo("sessao.encerrada");
        ClienteNavegador aluno = alunoConectado(ana);

        aluno.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), ana, "d", SALA_LAT, SALA_LON));

        assertEquals("SESSAO_ENCERRADA", aluno.proximo("erro").get("dados").get("codigo").stringValue());
    }

    @Test
    void professorNaoFazCheckin() throws Exception {
        ClienteNavegador professor = professor();
        Sessao sessao = abrirSessao(professor, "QRCODE", salaId);

        professor.enviar(checkinQr(sessao, tokenAtual(sessao.segredo()), professorId, "d", SALA_LAT, SALA_LON));

        assertEquals("PERMISSAO_NEGADA", professor.proximo("erro").get("dados").get("codigo").stringValue());
    }

    private long presencasDoAluno(String alunoId) {
        return MongoDeTeste.banco().getCollection("presencas").countDocuments(eq("alunoId", alunoId));
    }
}
