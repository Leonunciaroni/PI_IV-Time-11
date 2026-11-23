/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.Erro;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDeModoAlterado;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDePresencaAtualizada;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDeSessaoEncerrada;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeTrocarModo;
import br.edu.puccampinas.fastattendance.common.comunicado.PresenteResumo;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeTrocarModo;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.totp.ValidadorTotp;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code sessao.trocar_modo} (#49) pelo transporte por sockets, com o MongoDB real: troca entre os três
 * modos sem perder presenças, regras de sala, idempotência, permissões, eventos aos acompanhantes (na
 * mesma réplica e em outra) e corridas com o encerramento.
 */
class TrocarModoTest {

    private static final double SALA_LAT = -22.8339;
    private static final double SALA_LON = -47.0525;
    private static final double PERTO_LAT = SALA_LAT + 0.0001;

    private final JwtService jwt = new JwtService(ServidorDeTeste.SEGREDO, Duration.ofHours(1));
    private final List<ClienteTeste> clientes = new ArrayList<>();
    private final List<ServidorDeTeste> servidores = new ArrayList<>();
    private final MongoCollection<Document> sessoes = MongoDeTeste.banco().getCollection("sessoes");
    private final MongoCollection<Document> presencas = MongoDeTeste.banco().getCollection("presencas");

    private String professorId;
    private String turmaId;
    private String salaId;
    private String outraSalaId;
    private String ana;
    private String bruno;
    private String carla;

    /** Sessão aberta: identificador e segredo TOTP, para gerar tokens. */
    private record Sessao(String id, String segredo) {
    }

    @BeforeEach
    void preparar() throws Exception {
        MongoDeTeste.limpar();
        professorId = new ObjectId().toHexString();
        turmaId = inserir("turmas", new Document("nome", "Turma A").append("professorId", professorId));
        salaId = inserir("salas", new Document("nome", "H15 - Sala 203").append("latitude", SALA_LAT)
                .append("longitude", SALA_LON).append("raioMetros", 50.0));
        outraSalaId = inserir("salas", new Document("nome", "H15 - Sala 204").append("latitude", SALA_LAT)
                .append("longitude", SALA_LON).append("raioMetros", 50.0));
        ana = aluno("Ana Souza");
        bruno = aluno("Bruno Lima");
        carla = aluno("Carla Dias");
        servidores.add(new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofHours(4)));
    }

    @AfterEach
    void parar() throws Exception {
        clientes.forEach(ClienteTeste::close);
        for (ServidorDeTeste servidor : servidores) {
            servidor.close();
        }
    }

    // ---- Troca e persistência ----

    @Test
    void trocaDeClassicaParaQrCodeRespondeEGravaModoSalaEHistorico() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);

        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.QRCODE, salaId));

        RespostaDeTrocarModo resposta = professor.proximo(RespostaDeTrocarModo.class);
        assertEquals(sessao.id(), resposta.getSessaoId());
        assertEquals(ModoChamada.QRCODE, resposta.getModo());
        assertEquals(salaId, resposta.getSalaId());

        Document gravada = sessao(sessao.id());
        assertEquals("QRCODE", gravada.getString("modo"));
        assertEquals(salaId, gravada.getString("salaId"));
        assertEquals("ABERTA", gravada.getString("status"));
        assertEquals(resposta.getAlteradoEm(), gravada.getDate("modoAlteradoEm").getTime());
        assertTrue(resposta.getAlteradoEm() >= gravada.getDate("abertaEm").getTime());
        List<Document> trocas = gravada.getList("trocasDeModo", Document.class);
        assertEquals(1, trocas.size());
        assertEquals("CLASSICA", trocas.getFirst().getString("de"));
        assertEquals("QRCODE", trocas.getFirst().getString("para"));
        assertEquals(salaId, trocas.getFirst().getString("salaId"));
        assertEquals(resposta.getAlteradoEm(), trocas.getFirst().getDate("em").getTime());
    }

    @Test
    void trocaNaoMudaOSegredoTotpNemApagaPresencas() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        marcar(professor, sessao, ana, true);
        marcar(professor, sessao, bruno, false);

        trocar(professor, sessao, ModoChamada.SOM, null);

        assertEquals(sessao.segredo(), sessao(sessao.id()).getString("segredoTotp"));
        assertEquals(2, presencas.countDocuments(eq("sessaoId", sessao.id())));
        assertEquals(1, presencas.countDocuments(and(eq("sessaoId", sessao.id()), eq("presente", true))));
    }

    // ---- Presenças preservadas e regras do modo novo ----

    @Test
    void somParaQrCodeMantemPresencaPorSomEPassaAAceitarQrCode() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.SOM, null);
        checkinSom(sessao, ana, "disp-ana").proximo(RespostaDeCheckin.class);

        trocar(professor, sessao, ModoChamada.QRCODE, salaId);

        checkinQr(sessao, bruno, "disp-bruno").proximo(RespostaDeCheckin.class);
        // O canal antigo deixa de valer, e a presença por som continua única
        checkinSom(sessao, carla, "disp-carla").proximoErro(CodigoErro.MODO_INCOMPATIVEL);
        checkinQr(sessao, ana, "disp-ana-2").proximoErro(CodigoErro.PRESENCA_DUPLICADA);

        professor.enviar(new PedidoDeEncerrarSessao(sessao.id()));
        assertEquals(2, professor.proximo(RespostaDeEncerrarSessao.class).getTotalPresentes());
        Map<String, String> origens = origens(sessao.id());
        assertEquals("SOM", origens.get(ana));
        assertEquals("QRCODE", origens.get(bruno));
    }

    @Test
    void qrCodeParaClassicaMantemPresencaPorQrEPassaAAceitarMarcacaoManual() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.QRCODE, salaId);
        checkinQr(sessao, ana, "disp-ana").proximo(RespostaDeCheckin.class);
        professor.enviar(new PedidoDeMarcarPresenca(sessao.id(), bruno, true));
        professor.proximoErro(CodigoErro.MODO_INCOMPATIVEL);

        trocar(professor, sessao, ModoChamada.CLASSICA, null);

        marcar(professor, sessao, bruno, true);
        checkinQr(sessao, carla, "disp-carla").proximoErro(CodigoErro.MODO_INCOMPATIVEL);
        RespostaDeAcompanharSessao estado = acompanhar(professor(), sessao);
        assertEquals(ModoChamada.CLASSICA, estado.getModo());
        Map<String, OrigemPresenca> presentes = estado.getPresentes().stream()
                .collect(Collectors.toMap(PresenteResumo::getAlunoId, PresenteResumo::getOrigem));
        assertEquals(Map.of(ana, OrigemPresenca.QRCODE, bruno, OrigemPresenca.MANUAL), presentes);
    }

    @Test
    void depoisDeIrParaAClassicaOProfessorCorrigePresencaRegistradaPorSom() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.SOM, null);
        checkinSom(sessao, ana, "disp-ana").proximo(RespostaDeCheckin.class);

        trocar(professor, sessao, ModoChamada.CLASSICA, null);
        professor.enviar(new PedidoDeMarcarPresenca(sessao.id(), ana, false));

        assertFalse(professor.proximo(RespostaDeMarcarPresenca.class).isPresente());
        assertEquals(0, presencas.countDocuments(and(eq("sessaoId", sessao.id()), eq("presente", true))));
    }

    @Test
    void tokenGeradoComOSegredoDaAberturaValeNoCanalNovo() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.QRCODE, salaId);

        trocar(professor, sessao, ModoChamada.SOM, null);

        // O app do professor continua usando o segredo recebido em sessao.aberta
        checkinSom(sessao, ana, "disp-ana").proximo(RespostaDeCheckin.class);
    }

    @Test
    void trocasSeguidasPelosTresModosNaoPerdemNenhumaPresenca() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        marcar(professor, sessao, ana, true);
        trocar(professor, sessao, ModoChamada.QRCODE, salaId);
        checkinQr(sessao, bruno, "disp-bruno").proximo(RespostaDeCheckin.class);
        trocar(professor, sessao, ModoChamada.SOM, null);
        checkinSom(sessao, carla, "disp-carla").proximo(RespostaDeCheckin.class);
        trocar(professor, sessao, ModoChamada.CLASSICA, null);

        assertEquals(Map.of(ana, "MANUAL", bruno, "QRCODE", carla, "SOM"), origens(sessao.id()));
        List<String> caminho = sessao(sessao.id()).getList("trocasDeModo", Document.class).stream()
                .map(t -> t.getString("de") + ">" + t.getString("para")).toList();
        assertEquals(List.of("CLASSICA>QRCODE", "QRCODE>SOM", "SOM>CLASSICA"), caminho);
    }

    // ---- Sala ----

    @Test
    void irParaQrCodeExigeSalaExistente() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.SOM, null);

        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.QRCODE, null));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.QRCODE, "   "));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.QRCODE, new ObjectId().toHexString()));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.QRCODE, "nao-e-um-objectid"));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);

        Document gravada = sessao(sessao.id());
        assertEquals("SOM", gravada.getString("modo"));
        assertNull(gravada.get("trocasDeModo"));
        assertNull(gravada.get("modoAlteradoEm"));
    }

    @Test
    void salaInexistenteERecusadaMesmoIndoParaOutroModo() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);

        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.SOM, new ObjectId().toHexString()));

        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
        assertEquals("CLASSICA", sessao(sessao.id()).getString("modo"));
    }

    @Test
    void voltarAoQrCodeSemSalaReaproveitaASalaAnterior() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.QRCODE, salaId);
        trocar(professor, sessao, ModoChamada.CLASSICA, null);

        RespostaDeTrocarModo resposta = trocar(professor, sessao, ModoChamada.QRCODE, null);

        assertEquals(salaId, resposta.getSalaId());
        assertEquals(salaId, sessao(sessao.id()).getString("salaId"));
        checkinQr(sessao, ana, "disp-ana").proximo(RespostaDeCheckin.class);
    }

    @Test
    void trocarSoASalaNoQrCodeValeComoTroca() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.QRCODE, salaId);

        RespostaDeTrocarModo resposta = trocar(professor, sessao, ModoChamada.QRCODE, outraSalaId);

        assertEquals(outraSalaId, resposta.getSalaId());
        Document gravada = sessao(sessao.id());
        assertEquals(outraSalaId, gravada.getString("salaId"));
        assertEquals(1, gravada.getList("trocasDeModo", Document.class).size());
    }

    @Test
    void salaInformadaParaOutroModoFicaGuardadaParaUmaVoltaAoQrCode() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.SOM, null);

        trocar(professor, sessao, ModoChamada.CLASSICA, salaId);
        RespostaDeTrocarModo resposta = trocar(professor, sessao, ModoChamada.QRCODE, null);

        assertEquals(salaId, resposta.getSalaId());
    }

    // ---- Idempotência ----

    @Test
    void mesmoModoESalaNaoGravamNadaNemGeramEvento() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        ClienteTeste acompanhante = professor();
        acompanhar(acompanhante, sessao);

        RespostaDeTrocarModo resposta = trocar(professor, sessao, ModoChamada.CLASSICA, null);

        Document gravada = sessao(sessao.id());
        assertEquals(ModoChamada.CLASSICA, resposta.getModo());
        assertNull(resposta.getSalaId());
        assertEquals(gravada.getDate("abertaEm").getTime(), resposta.getAlteradoEm());
        assertNull(gravada.get("modoAlteradoEm"));
        assertNull(gravada.get("trocasDeModo"));
        assertNull(acompanhante.aguardar(700));
    }

    @Test
    void repetirATrocaDevolveOMesmoEstadoSemNovoRegistro() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);

        RespostaDeTrocarModo primeira = trocar(professor, sessao, ModoChamada.SOM, null);
        RespostaDeTrocarModo segunda = trocar(professor, sessao, ModoChamada.SOM, null);

        assertEquals(primeira.getAlteradoEm(), segunda.getAlteradoEm());
        assertEquals(1, sessao(sessao.id()).getList("trocasDeModo", Document.class).size());
    }

    // ---- Permissões, estados e campos ----

    @Test
    void alunoNaoTrocaModoNemDaPropriaTurma() throws Exception {
        Sessao sessao = abrir(professor(), ModoChamada.CLASSICA, null);
        ClienteTeste aluno = autenticado(porta(0), ana, Papel.ALUNO);

        aluno.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.SOM, null));
        aluno.proximoErro(CodigoErro.PERMISSAO_NEGADA);
        // O papel é conferido antes de tudo: nem os campos são validados para o aluno
        aluno.enviar(new PedidoDeTrocarModo(null, null, null));
        aluno.proximoErro(CodigoErro.PERMISSAO_NEGADA);

        assertEquals("CLASSICA", sessao(sessao.id()).getString("modo"));
    }

    @Test
    void outroProfessorNaoTrocaModo() throws Exception {
        Sessao sessao = abrir(professor(), ModoChamada.CLASSICA, null);
        ClienteTeste outro = autenticado(porta(0), new ObjectId().toHexString(), Papel.PROFESSOR);

        outro.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.SOM, null));

        outro.proximoErro(CodigoErro.PERMISSAO_NEGADA);
        assertEquals("CLASSICA", sessao(sessao.id()).getString("modo"));
    }

    @Test
    void sessaoInexistenteOuComIdInvalidoNaoEEncontrada() throws Exception {
        ClienteTeste professor = professor();

        professor.enviar(new PedidoDeTrocarModo(new ObjectId().toHexString(), ModoChamada.SOM, null));
        professor.proximoErro(CodigoErro.SESSAO_NAO_ENCONTRADA);
        professor.enviar(new PedidoDeTrocarModo("nao-e-um-objectid", ModoChamada.SOM, null));
        professor.proximoErro(CodigoErro.SESSAO_NAO_ENCONTRADA);
    }

    @Test
    void camposObrigatoriosAusentesSaoMensagemInvalida() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);

        professor.enviar(new PedidoDeTrocarModo(null, ModoChamada.SOM, null));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
        professor.enviar(new PedidoDeTrocarModo("  ", ModoChamada.SOM, null));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
        professor.enviar(new PedidoDeTrocarModo(sessao.id(), null, null));
        professor.proximoErro(CodigoErro.MENSAGEM_INVALIDA);
    }

    @Test
    void sessaoEncerradaNaoTrocaModo() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        professor.enviar(new PedidoDeEncerrarSessao(sessao.id()));
        professor.proximo(RespostaDeEncerrarSessao.class);

        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.SOM, null));

        professor.proximoErro(CodigoErro.SESSAO_ENCERRADA);
        assertEquals("CLASSICA", sessao(sessao.id()).getString("modo"));
    }

    @Test
    void sessaoVencidaPorTempoEEncerradaEmVezDeTrocada() throws Exception {
        ObjectId id = new ObjectId();
        sessoes.insertOne(new Document("_id", id).append("turmaId", turmaId).append("professorId", professorId)
                .append("modo", "SOM").append("segredoTotp", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")
                .append("status", "ABERTA")
                .append("abertaEm", new Date(System.currentTimeMillis() - Duration.ofHours(5).toMillis())));
        ClienteTeste professor = professor();

        professor.enviar(new PedidoDeTrocarModo(id.toHexString(), ModoChamada.QRCODE, salaId));

        professor.proximoErro(CodigoErro.SESSAO_ENCERRADA);
        Document gravada = sessao(id.toHexString());
        assertEquals("ENCERRADA", gravada.getString("status"));
        assertEquals("TEMPO_LIMITE", gravada.getString("motivoEncerramento"));
        assertEquals("SOM", gravada.getString("modo"));
    }

    // ---- Eventos aos acompanhantes ----

    @Test
    void acompanhanteRecebeOEventoComOsMesmosDadosDaResposta() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        ClienteTeste acompanhante = professor();
        acompanhar(acompanhante, sessao);

        RespostaDeTrocarModo resposta = trocar(professor, sessao, ModoChamada.QRCODE, salaId);

        EventoDeModoAlterado evento = acompanhante.proximo(EventoDeModoAlterado.class);
        assertEquals(sessao.id(), evento.getSessaoId());
        assertEquals(ModoChamada.QRCODE, evento.getModo());
        assertEquals(salaId, evento.getSalaId());
        assertEquals(resposta.getAlteradoEm(), evento.getAlteradoEm());
        // Um evento por troca
        assertNull(acompanhante.aguardar(500));
    }

    @Test
    void quemTrocaEAcompanhaRecebeRespostaEEvento() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        acompanhar(professor, sessao);

        professor.enviar(new PedidoDeTrocarModo(sessao.id(), ModoChamada.SOM, null));

        // Resposta e evento chegam em ordem indefinida: os dois devem aparecer, uma vez cada
        List<Comunicado> recebidos = List.of(professor.proximo(), professor.proximo());
        assertEquals(1, recebidos.stream().filter(RespostaDeTrocarModo.class::isInstance).count());
        assertEquals(1, recebidos.stream().filter(EventoDeModoAlterado.class::isInstance).count());
        assertNull(professor.aguardar(500));
    }

    @Test
    void eventoChegaAoAcompanhanteEmOutraReplica() throws Exception {
        ServidorDeTeste replicaB = new ServidorDeTeste(Duration.ofSeconds(5), Duration.ofSeconds(60));
        servidores.add(replicaB);
        ClienteTeste professorA = professor();
        Sessao sessao = abrir(professorA, ModoChamada.SOM, null);
        ClienteTeste acompanhanteB = autenticado(replicaB.porta(), professorId, Papel.PROFESSOR);
        acompanhar(acompanhanteB, sessao);

        trocar(professorA, sessao, ModoChamada.QRCODE, salaId);

        EventoDeModoAlterado evento = acompanhanteB.proximo(EventoDeModoAlterado.class);
        assertEquals(ModoChamada.QRCODE, evento.getModo());
        assertNull(acompanhanteB.aguardar(500));
    }

    @Test
    void acompanhanteContinuaRecebendoPresencasDepoisDaTroca() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        ClienteTeste acompanhante = professor();
        acompanhar(acompanhante, sessao);

        trocar(professor, sessao, ModoChamada.SOM, null);
        acompanhante.proximo(EventoDeModoAlterado.class);
        checkinSom(sessao, ana, "disp-ana").proximo(RespostaDeCheckin.class);

        EventoDePresencaAtualizada presenca = acompanhante.proximo(EventoDePresencaAtualizada.class);
        assertEquals(ana, presenca.getAlunoId());
        assertEquals(OrigemPresenca.SOM, presenca.getOrigem());
    }

    @Test
    void quemNaoAcompanhaNaoRecebeOEvento() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        ClienteTeste naoAcompanha = professor();

        trocar(professor, sessao, ModoChamada.SOM, null);

        assertNull(naoAcompanha.aguardar(700));
    }

    @Test
    void outrasAtualizacoesDaSessaoAbertaNaoGeramEventoDeModo() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.CLASSICA, null);
        ClienteTeste acompanhante = professor();
        acompanhar(acompanhante, sessao);

        // Uma atualização qualquer, que não é troca de modo, numa sessão ainda aberta
        sessoes.updateOne(eq("_id", new ObjectId(sessao.id())), set("observacao", "teste"));
        assertNull(acompanhante.aguardar(700));

        professor.enviar(new PedidoDeEncerrarSessao(sessao.id()));
        professor.proximo(RespostaDeEncerrarSessao.class);
        assertInstanceOf(EventoDeSessaoEncerrada.class, acompanhante.proximo());
        assertNull(acompanhante.aguardar(500));
    }

    @Test
    void estadoDoAcompanharMostraOModoNovo() throws Exception {
        ClienteTeste professor = professor();
        Sessao sessao = abrir(professor, ModoChamada.QRCODE, salaId);

        trocar(professor, sessao, ModoChamada.SOM, null);

        assertEquals(ModoChamada.SOM, acompanhar(professor(), sessao).getModo());
    }

    // ---- Corridas ----

    @Test
    void trocaEEncerramentoAoMesmoTempoTerminamSempreConsistentes() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 0; rodada < 15; rodada++) {
                ClienteTeste quemTroca = professor();
                ClienteTeste quemEncerra = professor();
                Sessao sessao = abrir(quemTroca, ModoChamada.CLASSICA, null);
                CountDownLatch largada = new CountDownLatch(1);

                Future<Comunicado> troca = executor.submit(enviarJunto(largada, quemTroca,
                        new PedidoDeTrocarModo(sessao.id(), ModoChamada.SOM, null)));
                Future<Comunicado> encerramento = executor.submit(enviarJunto(largada, quemEncerra,
                        new PedidoDeEncerrarSessao(sessao.id())));
                largada.countDown();

                assertInstanceOf(RespostaDeEncerrarSessao.class, encerramento.get(10, TimeUnit.SECONDS));
                Comunicado resultado = troca.get(10, TimeUnit.SECONDS);
                Document gravada = sessao(sessao.id());
                assertEquals("ENCERRADA", gravada.getString("status"));
                if (resultado instanceof Erro erro) {
                    assertEquals(CodigoErro.SESSAO_ENCERRADA, erro.getCodigo(), "rodada " + rodada);
                    assertEquals("CLASSICA", gravada.getString("modo"), "rodada " + rodada);
                } else {
                    assertInstanceOf(RespostaDeTrocarModo.class, resultado, "rodada " + rodada);
                    assertEquals("SOM", gravada.getString("modo"), "rodada " + rodada);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duasTrocasAoMesmoTempoDeixamOHistoricoCoerenteComOModoFinal() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 0; rodada < 15; rodada++) {
                ClienteTeste primeiro = professor();
                ClienteTeste segundo = professor();
                Sessao sessao = abrir(primeiro, ModoChamada.CLASSICA, null);
                CountDownLatch largada = new CountDownLatch(1);

                Future<Comunicado> paraSom = executor.submit(enviarJunto(largada, primeiro,
                        new PedidoDeTrocarModo(sessao.id(), ModoChamada.SOM, null)));
                Future<Comunicado> paraQr = executor.submit(enviarJunto(largada, segundo,
                        new PedidoDeTrocarModo(sessao.id(), ModoChamada.QRCODE, salaId)));
                largada.countDown();

                assertInstanceOf(RespostaDeTrocarModo.class, paraSom.get(10, TimeUnit.SECONDS), "rodada " + rodada);
                assertInstanceOf(RespostaDeTrocarModo.class, paraQr.get(10, TimeUnit.SECONDS), "rodada " + rodada);
                Document gravada = sessao(sessao.id());
                List<Document> trocas = gravada.getList("trocasDeModo", Document.class);
                assertFalse(trocas.isEmpty(), "rodada " + rodada);
                // A última troca registrada é a que vale
                assertEquals(trocas.getLast().getString("para"), gravada.getString("modo"), "rodada " + rodada);

                quemFecha(primeiro, sessao);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- Apoio ----

    private static Callable<Comunicado> enviarJunto(CountDownLatch largada, ClienteTeste cliente, Comunicado pedido) {
        return () -> {
            largada.await();
            cliente.enviar(pedido);
            return cliente.proximo();
        };
    }

    private void quemFecha(ClienteTeste professor, Sessao sessao) throws Exception {
        professor.enviar(new PedidoDeEncerrarSessao(sessao.id()));
        professor.proximo(RespostaDeEncerrarSessao.class);
    }

    private int porta(int indice) {
        return servidores.get(indice).porta();
    }

    private ClienteTeste professor() throws Exception {
        return autenticado(porta(0), professorId, Papel.PROFESSOR);
    }

    private ClienteTeste autenticado(int porta, String usuarioId, Papel papel) throws Exception {
        ClienteTeste cliente = new ClienteTeste(porta);
        clientes.add(cliente);
        cliente.enviar(new PedidoDeAutenticacao(jwt.emitir(usuarioId, "Usuário", papel).token()));
        cliente.proximo(RespostaDeAutenticacao.class);
        return cliente;
    }

    private Sessao abrir(ClienteTeste professor, ModoChamada modo, String sala) throws Exception {
        professor.enviar(new PedidoDeAbrirSessao(turmaId, modo, sala));
        RespostaDeAbrirSessao aberta = professor.proximo(RespostaDeAbrirSessao.class);
        return new Sessao(aberta.getSessaoId(), aberta.getTotp().getSegredo());
    }

    private RespostaDeTrocarModo trocar(ClienteTeste professor, Sessao sessao, ModoChamada modo, String sala)
            throws Exception {
        professor.enviar(new PedidoDeTrocarModo(sessao.id(), modo, sala));
        return professor.proximo(RespostaDeTrocarModo.class);
    }

    private void marcar(ClienteTeste professor, Sessao sessao, String alunoId, boolean presente) throws Exception {
        professor.enviar(new PedidoDeMarcarPresenca(sessao.id(), alunoId, presente));
        professor.proximo(RespostaDeMarcarPresenca.class);
    }

    private RespostaDeAcompanharSessao acompanhar(ClienteTeste cliente, Sessao sessao) throws Exception {
        cliente.enviar(new PedidoDeAcompanharSessao(sessao.id()));
        return cliente.proximo(RespostaDeAcompanharSessao.class);
    }

    /** Aluno numa conexão própria, já com o check-in por som enviado; a resposta fica para o teste ler. */
    private ClienteTeste checkinSom(Sessao sessao, String alunoId, String dispositivo) throws Exception {
        ClienteTeste aluno = autenticado(porta(0), alunoId, Papel.ALUNO);
        aluno.enviar(new PedidoDeCheckin(sessao.id(), tokenAtual(sessao), OrigemPresenca.SOM, dispositivo,
                null, null, null));
        return aluno;
    }

    /** Aluno numa conexão própria, já com o check-in por QR Code enviado de dentro da sala. */
    private ClienteTeste checkinQr(Sessao sessao, String alunoId, String dispositivo) throws Exception {
        ClienteTeste aluno = autenticado(porta(0), alunoId, Papel.ALUNO);
        aluno.enviar(new PedidoDeCheckin(sessao.id(), tokenAtual(sessao), OrigemPresenca.QRCODE, dispositivo,
                PERTO_LAT, SALA_LON, 10.0));
        return aluno;
    }

    private static String tokenAtual(Sessao sessao) {
        return ValidadorTotp.gerar(sessao.segredo(), System.currentTimeMillis() / 1000);
    }

    private Document sessao(String sessaoId) {
        return sessoes.find(eq("_id", new ObjectId(sessaoId))).first();
    }

    /** Origem da presença de cada aluno presente na sessão. */
    private Map<String, String> origens(String sessaoId) {
        Map<String, String> origens = new java.util.HashMap<>();
        for (Document presenca : presencas.find(and(eq("sessaoId", sessaoId), eq("presente", true)))) {
            origens.put(presenca.getString("alunoId"), presenca.getString("origem"));
        }
        return origens;
    }

    private String aluno(String nome) {
        String id = inserir("usuarios", new Document("nome", nome).append("papel", "ALUNO"));
        inserir("matriculas", new Document("turmaId", turmaId).append("alunoId", id));
        return id;
    }

    private static String inserir(String colecao, Document documento) {
        ObjectId id = new ObjectId();
        MongoDeTeste.banco().getCollection(colecao).insertOne(documento.append("_id", id));
        return id.toHexString();
    }
}
