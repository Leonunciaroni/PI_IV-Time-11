package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.Erro;
import br.edu.puccampinas.fastattendance.sessao.chamada.Haversine;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.totp.SegredoTotp;
import br.edu.puccampinas.fastattendance.sessao.totp.ValidadorTotp;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/** {@code checkin} pelo canal QR Code e pelo som, com o MongoDB real e as regras antifraude (docs/protocolo-v1.md, 4.8 e 7). */
class CheckinTest {

    private static final String SEGREDO = ServidorDeTeste.SEGREDO;

    /** Sala de referência: coordenadas do exemplo da seção 9 do protocolo, raio de 50 m. */
    private static final double SALA_LAT = -22.8339;
    private static final double SALA_LON = -47.0525;
    private static final double RAIO_SALA = 50;

    /** 0,0001 grau de latitude (cerca de 11 m) e 0,001 grau (cerca de 111 m). */
    private static final double PERTO_LAT = SALA_LAT + 0.0001;
    private static final double LONGE_LAT = SALA_LAT + 0.001;

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final MongoCollection<Document> presencas = MongoDeTeste.banco().getCollection("presencas");
    private ServidorDeTeste servidor;

    private String professorId;
    private String turmaId;
    private String salaId;
    private String ana;
    private String bruno;
    private String alunoDeOutraTurma;

    @BeforeEach
    void preparar() throws Exception {
        MongoDeTeste.limpar();
        professorId = new ObjectId().toHexString();
        turmaId = inserir("turmas", new Document("nome", "Turma A").append("professorId", professorId));
        salaId = inserir("salas", new Document("nome", "H15 - Sala 203")
                .append("latitude", SALA_LAT).append("longitude", SALA_LON).append("raioMetros", RAIO_SALA));
        ana = aluno("Ana Souza");
        bruno = aluno("Bruno Lima");
        alunoDeOutraTurma = aluno("Diego Alves");
        for (String alunoId : List.of(ana, bruno)) {
            inserir("matriculas", new Document("turmaId", turmaId).append("alunoId", alunoId));
        }
        servidor = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        servidor.close();
    }

    // ---- Caminho feliz e distância ----

    @Test
    void registraPresencaDeAlunoMatriculadoDentroDoRaio() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", PERTO_LAT, SALA_LON));

        RespostaDeCheckin resposta = aluno.proximo(RespostaDeCheckin.class);
        assertEquals(sessao.id(), resposta.getSessaoId());

        Document gravada = presencas.find(eq("alunoId", ana)).first();
        assertNotNull(gravada);
        assertEquals(sessao.id(), gravada.getString("sessaoId"));
        assertEquals(turmaId, gravada.getString("turmaId"));
        assertTrue(gravada.getBoolean("presente"));
        assertEquals("QRCODE", gravada.getString("origem"));
        assertEquals("disp-ana", gravada.getString("dispositivoId"));
        assertEquals(PERTO_LAT, gravada.getDouble("latitude"));
        assertEquals(resposta.getRegistradoEm(), gravada.getDate("registradoEm").getTime());
    }

    @Test
    void aceitaTokenDaJanelaAnterior() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        String tokenAnterior = ValidadorTotp.gerar(sessao.segredo(), instanteAgora() - SegredoTotp.PERIODO_SEGUNDOS);

        aluno.enviar(checkin(sessao, tokenAnterior, "disp-ana", SALA_LAT, SALA_LON));

        aluno.proximo(RespostaDeCheckin.class);
    }

    @Test
    void foraDoRaioRecusaComDistanciaNaMensagem() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", LONGE_LAT, SALA_LON));

        assertTrue(aluno.proximoErro(CodigoErro.FORA_DO_RAIO).getMensagem().contains("111"));
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Token ----

    @Test
    void tokenDeOutraSessaoRecusaComTokenInvalido() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        // Segredo Base32 do exemplo da seção 5.3 do protocolo, diferente do segredo desta sessão
        String token = ValidadorTotp.gerar("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", instanteAgora());

        aluno.enviar(checkin(sessao, token, "disp-ana", SALA_LAT, SALA_LON));

        aluno.proximoErro(CodigoErro.TOKEN_INVALIDO);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void tokenMalformadoRecusaComTokenInvalido() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin(sessao, "12ab56", "disp-ana", SALA_LAT, SALA_LON));

        aluno.proximoErro(CodigoErro.TOKEN_INVALIDO);
    }

    @Test
    void tokenDeJanelaJaExpiradaRecusaComTokenExpirado() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        // Duas janelas atrás: fora das aceitas (atual e anterior), mas ainda reconhecida como expirada
        String token = ValidadorTotp.gerar(sessao.segredo(), instanteAgora() - 2L * SegredoTotp.PERIODO_SEGUNDOS);

        aluno.enviar(checkin(sessao, token, "disp-ana", SALA_LAT, SALA_LON));

        aluno.proximoErro(CodigoErro.TOKEN_EXPIRADO);
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Matrícula ----

    @Test
    void recusaAlunoNaoMatriculadoNaTurma() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(alunoDeOutraTurma, Papel.ALUNO);

        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-diego", SALA_LAT, SALA_LON));

        aluno.proximoErro(CodigoErro.ALUNO_NAO_MATRICULADO);
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Duplicidade e dispositivo ----

    @Test
    void recusaSegundoCheckinDoMesmoAlunoComPresencaDuplicada() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));
        aluno.proximo(RespostaDeCheckin.class);

        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));
        aluno.proximoErro(CodigoErro.PRESENCA_DUPLICADA);

        // Mesmo com outro dispositivo, o aluno não registra duas vezes
        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-outro", SALA_LAT, SALA_LON));
        aluno.proximoErro(CodigoErro.PRESENCA_DUPLICADA);
        assertEquals(1, presencas.countDocuments(eq("sessaoId", sessao.id())));
    }

    @Test
    void mesmoDispositivoComOutroAlunoRecusaComDispositivoJaUtilizado() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste ana1 = autenticado(ana, Papel.ALUNO);
        ana1.enviar(checkin(sessao, tokenAtual(sessao), "disp-compartilhado", SALA_LAT, SALA_LON));
        ana1.proximo(RespostaDeCheckin.class);

        ClienteTeste bruno1 = autenticado(bruno, Papel.ALUNO);
        bruno1.enviar(checkin(sessao, tokenAtual(sessao), "disp-compartilhado", SALA_LAT, SALA_LON));

        bruno1.proximoErro(CodigoErro.DISPOSITIVO_JA_UTILIZADO);
        assertEquals(0, presencas.countDocuments(eq("alunoId", bruno)));
    }

    @Test
    void dispositivoPodeSerReutilizadoEmOutraSessao() throws Exception {
        Sessao primeira = abrirQrcode();
        ClienteTeste ana1 = autenticado(ana, Papel.ALUNO);
        ana1.enviar(checkin(primeira, tokenAtual(primeira), "disp-x", SALA_LAT, SALA_LON));
        ana1.proximo(RespostaDeCheckin.class);
        encerrar(primeira);

        Sessao segunda = abrirQrcode();
        ClienteTeste bruno1 = autenticado(bruno, Papel.ALUNO);
        bruno1.enviar(checkin(segunda, tokenAtual(segunda), "disp-x", SALA_LAT, SALA_LON));

        bruno1.proximo(RespostaDeCheckin.class);
    }

    // ---- Estado da sessão ----

    @Test
    void recusaSessaoEncerradaOuInexistente() throws Exception {
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        aluno.enviar(checkin(new Sessao(new ObjectId().toHexString(), null), "123456", "disp-ana",
                SALA_LAT, SALA_LON));
        aluno.proximoErro(CodigoErro.SESSAO_NAO_ENCONTRADA);

        Sessao sessao = abrirQrcode();
        encerrar(sessao);
        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));
        aluno.proximoErro(CodigoErro.SESSAO_ENCERRADA);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void recusaSessaoEmModoClassico() throws Exception {
        Sessao sessao = abrirSessao(ModoChamada.CLASSICA, null);
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));

        aluno.proximoErro(CodigoErro.MODO_INCOMPATIVEL);
    }

    @Test
    void canalSomEmSessaoQrcodeRecusaComModoIncompativel() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(new PedidoDeCheckin(sessao.id(), tokenAtual(sessao), OrigemPresenca.SOM, "disp-ana",
                null, null, null));

        aluno.proximoErro(CodigoErro.MODO_INCOMPATIVEL);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void canalQrcodeEmSessaoSomRecusaComModoIncompativel() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON));

        aluno.proximoErro(CodigoErro.MODO_INCOMPATIVEL);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void canalManualNaoEAceitoNoCheckin() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(new PedidoDeCheckin(sessao.id(), tokenAtual(sessao), OrigemPresenca.MANUAL, "disp-ana",
                null, null, null));

        aluno.proximoErro(CodigoErro.MODO_INCOMPATIVEL);
    }

    // ---- Canal SOM ----

    @Test
    void registraPresencaPeloSomSemCoordenadas() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-ana", null, null, null));

        aluno.proximo(RespostaDeCheckin.class);
        Document gravada = presencas.find(eq("alunoId", ana)).first();
        assertNotNull(gravada);
        assertEquals("SOM", gravada.getString("origem"));
        assertEquals("disp-ana", gravada.getString("dispositivoId"));
    }

    @Test
    void somIgnoraCoordenadasInvalidasSemGravaLas() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-ana", 123.0, 500.0, -5.0));

        aluno.proximo(RespostaDeCheckin.class);
        Document gravada = presencas.find(eq("alunoId", ana)).first();
        assertNotNull(gravada);
        assertFalse(gravada.containsKey("latitude"));
        assertFalse(gravada.containsKey("longitude"));
    }

    @Test
    void somIgnoraCoordenadasForaDoRaioSemGravaLas() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-ana", LONGE_LAT, SALA_LON, 10.0));

        aluno.proximo(RespostaDeCheckin.class);
        Document gravada = presencas.find(eq("alunoId", ana)).first();
        assertNotNull(gravada);
        assertFalse(gravada.containsKey("latitude"));
        assertFalse(gravada.containsKey("longitude"));
    }

    @Test
    void salaDistanteNaoAfetaSessaoSom() throws Exception {
        // Sala cadastrada longe da turma: a sessão SOM vinculada a ela ignora o raio
        String salaDistante = inserir("salas", new Document("nome", "Outro campus")
                .append("latitude", 0.0).append("longitude", 0.0).append("raioMetros", RAIO_SALA));
        Sessao sessao = abrirSessao(ModoChamada.SOM, salaDistante);
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-ana", SALA_LAT, SALA_LON, 10.0));

        aluno.proximo(RespostaDeCheckin.class);
    }

    @Test
    void somAceitaTokenDaJanelaAnterior() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        String tokenAnterior = ValidadorTotp.gerar(sessao.segredo(), instanteAgora() - SegredoTotp.PERIODO_SEGUNDOS);

        aluno.enviar(checkinSom(sessao, tokenAnterior, "disp-ana", null, null, null));

        aluno.proximo(RespostaDeCheckin.class);
    }

    @Test
    void somRecusaTokenInvalido() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        String token = ValidadorTotp.gerar("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", instanteAgora());

        aluno.enviar(checkinSom(sessao, token, "disp-ana", null, null, null));

        aluno.proximoErro(CodigoErro.TOKEN_INVALIDO);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void somRecusaTokenDeJanelaExpirada() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        String token = ValidadorTotp.gerar(sessao.segredo(), instanteAgora() - 2L * SegredoTotp.PERIODO_SEGUNDOS);

        aluno.enviar(checkinSom(sessao, token, "disp-ana", null, null, null));

        aluno.proximoErro(CodigoErro.TOKEN_EXPIRADO);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void somRecusaAlunoNaoMatriculado() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(alunoDeOutraTurma, Papel.ALUNO);

        aluno.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-diego", null, null, null));

        aluno.proximoErro(CodigoErro.ALUNO_NAO_MATRICULADO);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void somRecusaSegundoCheckinDoMesmoAlunoComPresencaDuplicada() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        aluno.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-ana", null, null, null));
        aluno.proximo(RespostaDeCheckin.class);

        aluno.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-outro", null, null, null));

        aluno.proximoErro(CodigoErro.PRESENCA_DUPLICADA);
        assertEquals(1, presencas.countDocuments(eq("sessaoId", sessao.id())));
    }

    @Test
    void somRecusaDispositivoJaUtilizadoPorOutroAluno() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste ana1 = autenticado(ana, Papel.ALUNO);
        ana1.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-compartilhado", null, null, null));
        ana1.proximo(RespostaDeCheckin.class);

        ClienteTeste bruno1 = autenticado(bruno, Papel.ALUNO);
        bruno1.enviar(checkinSom(sessao, tokenAtual(sessao), "disp-compartilhado", null, null, null));

        bruno1.proximoErro(CodigoErro.DISPOSITIVO_JA_UTILIZADO);
        assertEquals(0, presencas.countDocuments(eq("alunoId", bruno)));
    }

    @Test
    void somenteAlunoFazCheckin() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste professor = autenticado(professorId, Papel.PROFESSOR);

        professor.enviar(checkin(sessao, tokenAtual(sessao), "disp-prof", SALA_LAT, SALA_LON));

        professor.proximoErro(CodigoErro.PERMISSAO_NEGADA);
        assertEquals(0, presencas.countDocuments());
    }

    // ---- Campos ----

    @Test
    void exigeCamposDoCanalQrcode() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        // Sem latitude
        aluno.enviar(new PedidoDeCheckin(sessao.id(), tokenAtual(sessao), OrigemPresenca.QRCODE, "disp-ana",
                null, SALA_LON, 5.0));
        aluno.proximoErro(CodigoErro.MENSAGEM_INVALIDA);

        // Sem dispositivoId
        aluno.enviar(new PedidoDeCheckin(sessao.id(), tokenAtual(sessao), OrigemPresenca.QRCODE, null,
                SALA_LAT, SALA_LON, 5.0));
        aluno.proximoErro(CodigoErro.MENSAGEM_INVALIDA);

        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void coordenadaForaDaFaixaRecusaComMensagemInvalida() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);

        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-ana", 123.0, SALA_LON));

        aluno.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
    }

    // ---- Apoio ----

    @Test
    void aceitaDistanciaExatamenteIgualAoRaio() throws Exception {
        double distancia = Haversine.metros(SALA_LAT, SALA_LON, PERTO_LAT, SALA_LON);
        MongoDeTeste.banco().getCollection("salas").updateOne(eq("_id", new ObjectId(salaId)),
                new Document("$set", new Document("raioMetros", distancia)));
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        aluno.enviar(checkin(sessao, tokenAtual(sessao), "disp-limite", PERTO_LAT, SALA_LON));
        aluno.proximo(RespostaDeCheckin.class);
        assertEquals(1, presencas.countDocuments());
    }

    @Test
    void recusaDistanciaLogoAcimaDoRaioMesmoComPrecisaoAlta() throws Exception {
        double distancia = Haversine.metros(SALA_LAT, SALA_LON, PERTO_LAT, SALA_LON);
        MongoDeTeste.banco().getCollection("salas").updateOne(eq("_id", new ObjectId(salaId)),
                new Document("$set", new Document("raioMetros", Math.nextDown(distancia))));
        Sessao sessao = abrirQrcode();
        ClienteTeste aluno = autenticado(ana, Papel.ALUNO);
        aluno.enviar(new PedidoDeCheckin(sessao.id(), tokenAtual(sessao), OrigemPresenca.QRCODE,
                "disp-fora", PERTO_LAT, SALA_LON, 1000.0));
        aluno.proximoErro(CodigoErro.FORA_DO_RAIO);
        assertEquals(0, presencas.countDocuments());
    }

    @Test
    void doisCheckinsConcorrentesDoMesmoAlunoGravamUmaUnicaPresenca() throws Exception {
        Sessao sessao = abrirQrcode();
        ClienteTeste primeiro = autenticado(ana, Papel.ALUNO);
        ClienteTeste segundo = autenticado(ana, Papel.ALUNO);
        String token = tokenAtual(sessao);
        conferirConcorrencia(primeiro, checkin(sessao, token, "disp-1", SALA_LAT, SALA_LON),
                segundo, checkin(sessao, token, "disp-2", SALA_LAT, SALA_LON), CodigoErro.PRESENCA_DUPLICADA);
    }

    @Test
    void doisAlunosConcorrentesNoMesmoDispositivoGravamUmaUnicaPresenca() throws Exception {
        Sessao sessao = abrirSom();
        ClienteTeste primeiro = autenticado(ana, Papel.ALUNO);
        ClienteTeste segundo = autenticado(bruno, Papel.ALUNO);
        String token = tokenAtual(sessao);
        conferirConcorrencia(primeiro, checkinSom(sessao, token, "disp-unico", null, null, null),
                segundo, checkinSom(sessao, token, "disp-unico", null, null, null), CodigoErro.DISPOSITIVO_JA_UTILIZADO);
    }

    private void conferirConcorrencia(ClienteTeste primeiro, PedidoDeCheckin pedido1,
                                      ClienteTeste segundo, PedidoDeCheckin pedido2, CodigoErro esperado) throws Exception {
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch disparar = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var resposta1 = executor.submit(() -> {
                prontos.countDown();
                assertTrue(disparar.await(3, TimeUnit.SECONDS));
                primeiro.enviar(pedido1);
                return primeiro.proximo();
            });
            var resposta2 = executor.submit(() -> {
                prontos.countDown();
                assertTrue(disparar.await(3, TimeUnit.SECONDS));
                segundo.enviar(pedido2);
                return segundo.proximo();
            });
            assertTrue(prontos.await(3, TimeUnit.SECONDS));
            disparar.countDown();
            List<Comunicado> respostas = List.of(resposta1.get(5, TimeUnit.SECONDS), resposta2.get(5, TimeUnit.SECONDS));
            assertEquals(1, respostas.stream().filter(RespostaDeCheckin.class::isInstance).count());
            List<Erro> erros = respostas.stream().filter(Erro.class::isInstance).map(Erro.class::cast).toList();
            assertEquals(1, erros.size());
            assertEquals(esperado, erros.getFirst().getCodigo());
            assertEquals(1, presencas.countDocuments());
        }
    }

    /** Sessão aberta pelo professor, com o segredo TOTP da resposta {@code sessao.aberta}. */
    private record Sessao(String id, String segredo) {
    }

    private Sessao abrirQrcode() throws Exception {
        return abrirSessao(ModoChamada.QRCODE, salaId);
    }

    private Sessao abrirSom() throws Exception {
        return abrirSessao(ModoChamada.SOM, null);
    }

    private Sessao abrirSessao(ModoChamada modo, String salaIdOpcional) throws Exception {
        ClienteTeste professor = autenticado(professorId, Papel.PROFESSOR);
        professor.enviar(new PedidoDeAbrirSessao(turmaId, modo, salaIdOpcional));
        RespostaDeAbrirSessao resposta = professor.proximo(RespostaDeAbrirSessao.class);
        return new Sessao(resposta.getSessaoId(), resposta.getTotp().getSegredo());
    }

    private void encerrar(Sessao sessao) throws Exception {
        ClienteTeste professor = autenticado(professorId, Papel.PROFESSOR);
        professor.enviar(new PedidoDeEncerrarSessao(sessao.id()));
        professor.proximo(RespostaDeEncerrarSessao.class);
    }

    private static String tokenAtual(Sessao sessao) {
        return ValidadorTotp.gerar(sessao.segredo(), instanteAgora());
    }

    private static long instanteAgora() {
        return System.currentTimeMillis() / 1000;
    }

    private ClienteTeste autenticado(String usuarioId, Papel papel) throws Exception {
        ClienteTeste cliente = new ClienteTeste(servidor.porta());
        clientes.add(cliente);
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(new PedidoDeAutenticacao(token));
        cliente.proximo(RespostaDeAutenticacao.class);
        return cliente;
    }

    private String aluno(String nome) {
        return inserir("usuarios", new Document("nome", nome).append("papel", "ALUNO"));
    }

    private static String inserir(String colecao, Document documento) {
        ObjectId id = new ObjectId();
        MongoDeTeste.banco().getCollection(colecao).insertOne(documento.append("_id", id));
        return id.toHexString();
    }

    /** Check-in pelo QR Code, com a precisão padrão de 10 m. */
    private static PedidoDeCheckin checkin(Sessao sessao, String token, String dispositivo,
                                           double latitude, double longitude) {
        return new PedidoDeCheckin(sessao.id(), token, OrigemPresenca.QRCODE, dispositivo,
                latitude, longitude, 10.0);
    }

    /** Check-in pelo canal SOM; coordenadas e precisão são opcionais (o servidor as ignora). */
    private static PedidoDeCheckin checkinSom(Sessao sessao, String token, String dispositivo,
                                              Double latitude, Double longitude, Double precisao) {
        return new PedidoDeCheckin(sessao.id(), token, OrigemPresenca.SOM, dispositivo,
                latitude, longitude, precisao);
    }
}
