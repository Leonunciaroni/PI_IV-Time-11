package br.edu.puccampinas.fastattendance.ponte;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import static com.mongodb.client.model.Filters.eq;

/**
 * MongoDB descartável (replica set de um nó) para os testes de integração da ponte, iniciado uma vez
 * e compartilhado. Cria os mesmos índices da API e do servidor de sessão, porque as regras antifraude
 * dependem deles. Cópia do {@code MongoDeTeste} do {@code sessao}, que é package-private.
 */
final class MongoDeTeste {

    static final String BANCO = "fast_attendance_teste";

    private static MongoDBContainer conteiner;
    private static MongoClient cliente;

    private MongoDeTeste() {
    }

    static synchronized String uri() {
        if (conteiner == null) {
            conteiner = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();
            conteiner.start();
            cliente = MongoClients.create(conteiner.getReplicaSetUrl(BANCO));
            cliente.getDatabase(BANCO).getCollection("sessoes").createIndex(Indexes.ascending("turmaId"),
                    new IndexOptions().unique(true).name("turma_sessao_aberta_unica")
                            .partialFilterExpression(eq("status", "ABERTA")));
            MongoCollection<Document> presencas = cliente.getDatabase(BANCO).getCollection("presencas");
            presencas.createIndex(Indexes.ascending("alunoId", "sessaoId"),
                    new IndexOptions().unique(true).name("aluno_sessao_unico"));
            presencas.createIndex(Indexes.ascending("sessaoId", "dispositivoId"),
                    new IndexOptions().unique(true).name("sessao_dispositivo_unico")
                            .partialFilterExpression(new Document("dispositivoId", new Document("$type", 2))));
        }
        return conteiner.getReplicaSetUrl(BANCO);
    }

    static MongoDatabase banco() {
        uri();
        return cliente.getDatabase(BANCO);
    }

    /** Apaga os documentos sem remover os índices. */
    static void limpar() {
        for (String colecao : new String[]{"usuarios", "turmas", "salas", "sessoes", "presencas", "matriculas"}) {
            banco().getCollection(colecao).deleteMany(new Document());
        }
    }
}
