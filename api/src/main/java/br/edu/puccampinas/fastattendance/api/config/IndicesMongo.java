package br.edu.puccampinas.fastattendance.api.config;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

/**
 * Cria as coleções e os índices na inicialização da API. A operação é idempotente: o MongoDB
 * ignora índices já existentes com a mesma definição.
 * <p>
 * As regras de unicidade ficam no banco, e não só no código, para valer também para o servidor
 * de sessão e para escritas concorrentes em réplicas diferentes.
 */
@Component
public class IndicesMongo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndicesMongo.class);

    private final MongoTemplate mongo;

    public IndicesMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarColecoes();

        indices(Colecoes.USUARIOS)
                .createIndex(new Index("email", Direction.ASC).unique().named("email_unico"));

        indices(Colecoes.TURMAS)
                .createIndex(new Index("professorId", Direction.ASC).named("professor"));

        IndexOperations matriculas = indices(Colecoes.MATRICULAS);
        matriculas.createIndex(new Index().on("turmaId", Direction.ASC).on("alunoId", Direction.ASC)
                .unique().named("turma_aluno_unico"));
        matriculas.createIndex(new Index("alunoId", Direction.ASC).named("aluno"));

        IndexOperations sessoes = indices(Colecoes.SESSOES);
        sessoes.createIndex(new Index().on("turmaId", Direction.ASC).on("abertaEm", Direction.DESC)
                .named("turma_abertura"));
        // No máximo uma sessão aberta por turma (erro SESSAO_JA_ABERTA no protocolo)
        sessoes.createIndex(new Index("turmaId", Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is(StatusSessao.ABERTA.name())))
                .named("turma_sessao_aberta_unica"));

        IndexOperations presencas = indices(Colecoes.PRESENCAS);
        // Regra central: um aluno tem no máximo uma presença por sessão
        presencas.createIndex(new Index().on("alunoId", Direction.ASC).on("sessaoId", Direction.ASC)
                .unique().named("aluno_sessao_unico"));
        // Um dispositivo não registra presença para dois alunos na mesma sessão
        presencas.createIndex(new Index().on("sessaoId", Direction.ASC).on("dispositivoId", Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("dispositivoId").type(2)))
                .named("sessao_dispositivo_unico"));
        presencas.createIndex(new Index().on("turmaId", Direction.ASC).on("alunoId", Direction.ASC)
                .named("turma_aluno"));

        log.info("Coleções e índices do MongoDB verificados.");
    }

    private void criarColecoes() {
        for (String colecao : new String[]{Colecoes.USUARIOS, Colecoes.TURMAS, Colecoes.MATRICULAS,
                Colecoes.SALAS, Colecoes.SESSOES, Colecoes.PRESENCAS}) {
            if (!mongo.collectionExists(colecao)) {
                mongo.createCollection(colecao);
            }
        }
    }

    private IndexOperations indices(String colecao) {
        return mongo.indexOps(colecao);
    }
}
