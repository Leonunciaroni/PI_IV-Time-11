/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.chamada;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.MotivoEncerramento;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeTrocarModo;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeTrocarModo;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import br.edu.puccampinas.fastattendance.sessao.protocolo.ErroDeProtocolo;
import br.edu.puccampinas.fastattendance.sessao.totp.SegredoTotp;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.push;
import static com.mongodb.client.model.Updates.set;

/**
 * Abertura e encerramento das sessões de chamada (docs/protocolo-v1.md, seções 4.3 e 4.4).
 * <p>
 * O estado fica só no MongoDB, e não em memória, para que qualquer réplica do servidor de sessão
 * atenda qualquer sessão. As transições usam operações atômicas do banco: o índice único parcial
 * {@code turma_sessao_aberta_unica} (criado pela API) impede duas sessões abertas na mesma turma, e
 * o encerramento só altera documentos ainda com status {@code ABERTA}.
 */
public final class ServicoDeSessoes {

    private final MongoCollection<Document> turmas;
    private final MongoCollection<Document> salas;
    private final MongoCollection<Document> sessoes;
    private final MongoCollection<Document> presencas;
    private final Clock relogio;
    private final Duration duracaoMaxima;
    private final SecureRandom aleatorio = new SecureRandom();

    public ServicoDeSessoes(MongoDatabase banco, Clock relogio, Duration duracaoMaxima) {
        this.turmas = banco.getCollection(Colecoes.TURMAS);
        this.salas = banco.getCollection(Colecoes.SALAS);
        this.sessoes = banco.getCollection(Colecoes.SESSOES);
        this.presencas = banco.getCollection(Colecoes.PRESENCAS);
        this.relogio = relogio;
        this.duracaoMaxima = duracaoMaxima;
    }

    public RespostaDeAbrirSessao abrir(UsuarioAutenticado professor, PedidoDeAbrirSessao pedido) {
        exigirProfessor(professor);
        if (vazio(pedido.getTurmaId()) || pedido.getModo() == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Informe turmaId e modo");
        }
        String turmaId = pedido.getTurmaId().strip();
        Document turma = porId(turmas, turmaId);
        // Turma inexistente e turma de outro professor têm a mesma resposta, para não revelar quais existem
        if (turma == null || !professor.id().equals(turma.getString("professorId"))) {
            throw new ErroDeProtocolo(CodigoErro.PERMISSAO_NEGADA, "Turma não encontrada");
        }
        String salaId = vazio(pedido.getSalaId()) ? null : pedido.getSalaId().strip();
        if (pedido.getModo() == ModoChamada.QRCODE && salaId == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "O modo QRCODE exige salaId");
        }
        if (salaId != null && porId(salas, salaId) == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Sala não encontrada");
        }

        // Uma sessão esquecida aberta além do tempo limite não deve impedir a nova
        encerrarVencidas(eq("turmaId", turmaId));

        Instant agora = agora();
        ObjectId id = new ObjectId();
        String segredo = SegredoTotp.gerar(aleatorio);
        Document sessao = new Document("_id", id)
                .append("turmaId", turmaId)
                .append("professorId", professor.id())
                .append("modo", pedido.getModo().name())
                .append("segredoTotp", segredo)
                .append("status", StatusSessao.ABERTA.name())
                .append("abertaEm", Date.from(agora));
        if (salaId != null) {
            sessao.append("salaId", salaId);
        }
        try {
            sessoes.insertOne(sessao);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new ErroDeProtocolo(CodigoErro.SESSAO_JA_ABERTA, "Já existe uma sessão aberta para a turma");
            }
            throw e;
        }
        return new RespostaDeAbrirSessao(id.toHexString(), turmaId, pedido.getModo(), agora.toEpochMilli(),
                SegredoTotp.configuracao(segredo));
    }

    public RespostaDeEncerrarSessao encerrar(UsuarioAutenticado professor, String sessaoId) {
        exigirProfessor(professor);
        Document sessao = buscar(sessaoId);
        if (!professor.id().equals(sessao.getString("professorId"))) {
            throw new ErroDeProtocolo(CodigoErro.PERMISSAO_NEGADA, "A sessão é de outro professor");
        }
        exigirAberta(sessao);

        Instant agora = agora();
        Document encerrada = sessoes.findOneAndUpdate(
                and(eq("_id", sessao.getObjectId("_id")), eq("status", StatusSessao.ABERTA.name())),
                encerramento(MotivoEncerramento.PROFESSOR, agora),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (encerrada == null) {
            // Encerrada por outra conexão ou pelo tempo limite entre a leitura e a atualização
            throw new ErroDeProtocolo(CodigoErro.SESSAO_ENCERRADA, "A sessão já foi encerrada");
        }
        return new RespostaDeEncerrarSessao(sessaoId, MotivoEncerramento.PROFESSOR, agora.toEpochMilli(),
                totalPresentes(sessaoId));
    }

    /**
     * {@code sessao.trocar_modo}: muda o modo de uma chamada aberta sem tocar nas presenças, que são
     * gravadas por {@code sessaoId}, nem no segredo TOTP, que é o mesmo para QR Code e som.
     * <p>
     * Ordem das regras: papel de professor, campos obrigatórios, sessão existente, professor dono,
     * sessão aberta, sala. Ir para {@code QRCODE} exige sala: a do pedido ou, se ele não trouxer, a que
     * a sessão já tinha (de uma abertura ou troca anterior para QR Code). Pedir o modo e a sala atuais
     * não altera nada e devolve o estado atual, para que reenviar o pedido depois de uma reconexão não
     * gere erro nem evento.
     * <p>
     * A atualização é atômica e só vale para sessão ainda {@code ABERTA}: se ela for encerrada entre a
     * leitura e a escrita, a resposta é {@code SESSAO_ENCERRADA}. Cada troca fica registrada em
     * {@code trocasDeModo}. Os acompanhantes recebem o evento pelo change stream ({@link ObservadorDeMudancas}).
     */
    public RespostaDeTrocarModo trocarModo(UsuarioAutenticado professor, PedidoDeTrocarModo pedido) {
        exigirProfessor(professor);
        if (vazio(pedido.getSessaoId()) || pedido.getModo() == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Informe sessaoId e modo");
        }
        String sessaoId = pedido.getSessaoId().strip();
        Document sessao = buscar(sessaoId);
        if (!professor.id().equals(sessao.getString("professorId"))) {
            throw new ErroDeProtocolo(CodigoErro.PERMISSAO_NEGADA, "A sessão é de outro professor");
        }
        exigirAberta(sessao);

        String salaAtual = sessao.getString("salaId");
        String salaPedida = vazio(pedido.getSalaId()) ? null : pedido.getSalaId().strip();
        if (salaPedida != null && porId(salas, salaPedida) == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Sala não encontrada");
        }
        String salaNova = salaPedida != null ? salaPedida : salaAtual;
        if (pedido.getModo() == ModoChamada.QRCODE && salaNova == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "O modo QRCODE exige salaId");
        }

        ModoChamada modoAtual = ModoChamada.valueOf(sessao.getString("modo"));
        if (modoAtual == pedido.getModo() && Objects.equals(salaAtual, salaNova)) {
            return resposta(sessao);
        }

        Instant agora = agora();
        Document troca = new Document("de", modoAtual.name())
                .append("para", pedido.getModo().name())
                .append("salaId", salaNova)
                .append("em", Date.from(agora));
        Document trocada = sessoes.findOneAndUpdate(
                and(eq("_id", sessao.getObjectId("_id")), eq("status", StatusSessao.ABERTA.name())),
                combine(
                        set("modo", pedido.getModo().name()),
                        set("salaId", salaNova),
                        set("modoAlteradoEm", Date.from(agora)),
                        push("trocasDeModo", troca)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (trocada == null) {
            // Encerrada por outra conexão ou pelo tempo limite entre a leitura e a atualização
            throw new ErroDeProtocolo(CodigoErro.SESSAO_ENCERRADA, "A sessão já foi encerrada");
        }
        return resposta(trocada);
    }

    /** Modo atual da sessão; {@code alteradoEm} é a última troca ou, sem troca, a abertura. */
    private static RespostaDeTrocarModo resposta(Document sessao) {
        return new RespostaDeTrocarModo(sessao.getObjectId("_id").toHexString(),
                ModoChamada.valueOf(sessao.getString("modo")), sessao.getString("salaId"),
                momentoDoModo(sessao));
    }

    /** Momento em que a sessão passou ao modo atual: a última troca ou, sem troca, a abertura. */
    static long momentoDoModo(Document sessao) {
        Date alteradoEm = sessao.getDate("modoAlteradoEm");
        return (alteradoEm != null ? alteradoEm : sessao.getDate("abertaEm")).getTime();
    }

    /**
     * Sessão que ainda aceita registros. Deve ser chamada antes de qualquer registro de presença:
     * após o encerramento, nenhum registro é aceito.
     *
     * @throws ErroDeProtocolo {@code SESSAO_NAO_ENCONTRADA} ou {@code SESSAO_ENCERRADA}
     */
    public Document aberta(String sessaoId) {
        Document sessao = buscar(sessaoId);
        exigirAberta(sessao);
        return sessao;
    }

    /** Encerra com motivo {@code TEMPO_LIMITE} as sessões abertas há mais que a duração máxima. */
    public long encerrarVencidas() {
        return encerrarVencidas(new Document());
    }

    private long encerrarVencidas(Bson filtroAdicional) {
        Instant agora = agora();
        return sessoes.updateMany(
                and(filtroAdicional, eq("status", StatusSessao.ABERTA.name()),
                        lt("abertaEm", Date.from(agora.minus(duracaoMaxima)))),
                encerramento(MotivoEncerramento.TEMPO_LIMITE, agora)).getModifiedCount();
    }

    private void exigirAberta(Document sessao) {
        if (StatusSessao.ENCERRADA.name().equals(sessao.getString("status"))) {
            throw new ErroDeProtocolo(CodigoErro.SESSAO_ENCERRADA, "A sessão já foi encerrada");
        }
        Instant limite = sessao.getDate("abertaEm").toInstant().plus(duracaoMaxima);
        if (!agora().isBefore(limite)) {
            // Vencida, mas a varredura periódica ainda não passou por ela
            encerrarVencidas(eq("_id", sessao.getObjectId("_id")));
            throw new ErroDeProtocolo(CodigoErro.SESSAO_ENCERRADA, "A sessão foi encerrada por tempo limite");
        }
    }

    private Document buscar(String sessaoId) {
        Document sessao = vazio(sessaoId) ? null : porId(sessoes, sessaoId.strip());
        if (sessao == null) {
            throw new ErroDeProtocolo(CodigoErro.SESSAO_NAO_ENCONTRADA, "Sessão não encontrada");
        }
        return sessao;
    }

    int totalPresentes(String sessaoId) {
        return (int) presencas.countDocuments(and(eq("sessaoId", sessaoId), eq("presente", true)));
    }

    private static Bson encerramento(MotivoEncerramento motivo, Instant quando) {
        return combine(
                set("status", StatusSessao.ENCERRADA.name()),
                set("encerradaEm", Date.from(quando)),
                set("motivoEncerramento", motivo.name()));
    }

    private static void exigirProfessor(UsuarioAutenticado usuario) {
        if (usuario.papel() != Papel.PROFESSOR) {
            throw new ErroDeProtocolo(CodigoErro.PERMISSAO_NEGADA, "Apenas professores abrem e encerram sessões");
        }
    }

    /** Os ids gravados pela API são ObjectId; um texto que não é ObjectId não corresponde a nenhum documento. */
    private static Document porId(MongoCollection<Document> colecao, String id) {
        if (!ObjectId.isValid(id)) {
            return null;
        }
        return colecao.find(eq("_id", new ObjectId(id))).first();
    }

    /** O MongoDB guarda datas com precisão de milissegundos. */
    private Instant agora() {
        return relogio.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
