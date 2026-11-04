package br.edu.puccampinas.fastattendance.sessao.chamada;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import com.mongodb.MongoException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Alimenta o {@link PainelAoVivo} a partir dos Change Streams do MongoDB (docs/protocolo-v1.md,
 * seção 5.2).
 * <p>
 * Cada réplica do servidor de sessão abre os próprios streams, e cada uma entrega os eventos às
 * conexões que estão em <i>essa</i> réplica. Assim, uma presença gravada por qualquer réplica chega
 * aos professores acompanhando, em qualquer uma delas. Por isso as presenças gravadas pela chamada
 * clássica não geram push direto para os acompanhantes: o evento vem só daqui.
 * <p>
 * Os streams exigem replica set. Uma falha do stream (queda de rede, troca de primário, histórico
 * perdido) não derruba o servidor: o observador reabre o stream com backoff, retomando a partir do
 * último resume token conhecido.
 */
public final class ObservadorDeMudancas implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ObservadorDeMudancas.class.getName());

    /** Código do MongoDB para "histórico do oplog já descartado": o resume token não serve mais. */
    private static final int CODIGO_HISTORICO_PERDIDO = 286;
    private static final long ESPERA_MAXIMA_SEGUNDOS = 30;

    private final PainelAoVivo painel;
    private final MongoDatabase banco;
    private final Set<MongoChangeStreamCursor<ChangeStreamDocument<Document>>> abertos = ConcurrentHashMap.newKeySet();
    private volatile boolean ativo = true;

    public ObservadorDeMudancas(MongoDatabase banco, PainelAoVivo painel) {
        this.banco = banco;
        this.painel = painel;
    }

    /**
     * Abre os streams e inicia as threads que os consomem. A primeira abertura é feita aqui, antes
     * de retornar, para que nenhuma gravação feita logo após a inicialização fique de fora. Se falhar,
     * a thread tenta de novo com backoff.
     */
    public void iniciar() {
        iniciar(new Fluxo("presencas", banco.getCollection(Colecoes.PRESENCAS),
                List.of(Aggregates.match(Filters.in("operationType", List.of("insert", "update", "replace")))),
                this::tratarPresenca));
        iniciar(new Fluxo("sessoes", banco.getCollection(Colecoes.SESSOES),
                List.of(Aggregates.match(Filters.eq("operationType", "update"))),
                this::tratarSessao));
    }

    private void iniciar(Fluxo fluxo) {
        MongoChangeStreamCursor<ChangeStreamDocument<Document>> inicial = null;
        try {
            inicial = abrir(fluxo, null);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Change stream de " + fluxo.nome()
                    + " indisponível; nova tentativa em segundo plano", e);
        }
        MongoChangeStreamCursor<ChangeStreamDocument<Document>> primeiro = inicial;
        Thread.ofVirtual().name("sessao-change-stream-" + fluxo.nome())
                .start(() -> consumir(fluxo, primeiro));
    }

    private void consumir(Fluxo fluxo, MongoChangeStreamCursor<ChangeStreamDocument<Document>> primeiro) {
        MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = primeiro;
        BsonDocument ultimoToken = null;
        long espera = 1;
        while (ativo) {
            try {
                if (cursor == null) {
                    cursor = abrir(fluxo, ultimoToken);
                }
                espera = 1;
                while (ativo) {
                    // Bloqueia até a próxima alteração; o fechamento no shutdown interrompe a espera
                    ChangeStreamDocument<Document> evento = cursor.next();
                    try {
                        fluxo.tratador().accept(evento);
                    } catch (RuntimeException e) {
                        LOG.log(System.Logger.Level.WARNING, "Falha ao tratar evento de {0}", fluxo.nome());
                        LOG.log(System.Logger.Level.DEBUG, "Detalhes", e);
                    }
                    BsonDocument token = cursor.getResumeToken();
                    if (token != null) {
                        ultimoToken = token;
                    }
                }
            } catch (RuntimeException e) {
                if (!ativo) {
                    return;
                }
                if (historicoPerdido(e)) {
                    ultimoToken = null;
                }
                LOG.log(System.Logger.Level.WARNING,
                        "Change stream de " + fluxo.nome() + " interrompido; nova tentativa em " + espera + " s", e);
                fechar(cursor);
                cursor = null;
                if (!dormir(espera)) {
                    return;
                }
                espera = Math.min(espera * 2, ESPERA_MAXIMA_SEGUNDOS);
            }
        }
        fechar(cursor);
    }

    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> abrir(Fluxo fluxo, BsonDocument token) {
        ChangeStreamIterable<Document> iteravel = fluxo.colecao()
                .watch(fluxo.pipeline(), Document.class)
                .fullDocument(FullDocument.UPDATE_LOOKUP);
        if (token != null) {
            iteravel.resumeAfter(token);
        }
        MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = iteravel.cursor();
        abertos.add(cursor);
        return cursor;
    }

    private void tratarPresenca(ChangeStreamDocument<Document> evento) {
        // Null quando o documento some entre a alteração e a consulta do documento completo
        Document presenca = evento.getFullDocument();
        if (presenca != null) {
            painel.presencaGravada(presenca);
        }
    }

    private void tratarSessao(ChangeStreamDocument<Document> evento) {
        Document sessao = evento.getFullDocument();
        if (sessao != null && StatusSessao.ENCERRADA.name().equals(sessao.getString("status"))) {
            painel.sessaoEncerrada(sessao);
        }
    }

    private void fechar(MongoChangeStreamCursor<?> cursor) {
        if (cursor == null) {
            return;
        }
        abertos.removeIf(c -> c == cursor);
        try {
            cursor.close();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "Falha ao fechar change stream", e);
        }
    }

    /** Erros do stream que aparecem como {@code MongoException} com código 286. */
    private static boolean historicoPerdido(RuntimeException e) {
        return e instanceof MongoException mongo && mongo.getCode() == CODIGO_HISTORICO_PERDIDO;
    }

    private boolean dormir(long segundos) {
        try {
            Thread.sleep(Duration.ofSeconds(segundos));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Encerra os streams. As threads terminam quando a espera do próximo evento é interrompida. */
    @Override
    public void close() {
        ativo = false;
        for (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor : abertos) {
            fechar(cursor);
        }
    }

    private record Fluxo(String nome, MongoCollection<Document> colecao, List<Bson> pipeline,
                         Consumer<ChangeStreamDocument<Document>> tratador) {
    }
}
