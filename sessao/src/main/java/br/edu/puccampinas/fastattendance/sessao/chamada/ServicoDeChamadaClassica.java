package br.edu.puccampinas.fastattendance.sessao.chamada;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresencaAtualizada;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresencaLoteOk;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresencaMarcar;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresencaMarcarLote;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import br.edu.puccampinas.fastattendance.sessao.protocolo.ErroDeProtocolo;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

/**
 * Marcação manual de presença no modo clássico (docs/protocolo-v1.md, seções 4.6 e 4.7).
 * <p>
 * Cada aluno tem no máximo um documento de presença por sessão (índice único
 * {@code aluno_sessao_unico}); marcar e desmarcar apenas alteram o campo {@code presente} desse
 * documento.
 */
public final class ServicoDeChamadaClassica {

    private static final UpdateOptions UPSERT = new UpdateOptions().upsert(true);

    private final ServicoDeSessoes sessoes;
    private final MongoCollection<Document> matriculas;
    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> presencas;
    private final Clock relogio;

    public ServicoDeChamadaClassica(MongoDatabase banco, ServicoDeSessoes sessoes, Clock relogio) {
        this.sessoes = sessoes;
        this.matriculas = banco.getCollection(Colecoes.MATRICULAS);
        this.usuarios = banco.getCollection(Colecoes.USUARIOS);
        this.presencas = banco.getCollection(Colecoes.PRESENCAS);
        this.relogio = relogio;
    }

    public PresencaAtualizada marcar(UsuarioAutenticado professor, PresencaMarcar pedido) {
        Document sessao = sessaoClassicaDoProfessor(professor, pedido.sessaoId());
        String sessaoId = sessao.getObjectId("_id").toHexString();
        String turmaId = sessao.getString("turmaId");
        String alunoId = pedido.alunoId() == null ? "" : pedido.alunoId().strip();
        if (matriculas.countDocuments(and(eq("turmaId", turmaId), eq("alunoId", alunoId))) == 0) {
            throw new ErroDeProtocolo(CodigoErro.ALUNO_NAO_MATRICULADO, "Aluno não matriculado na turma");
        }

        Instant agora = agora();
        UpdateOneModel<Document> marcacao = marcacao(sessaoId, turmaId, alunoId, pedido.presente(), agora);
        try {
            presencas.updateOne(marcacao.getFilter(), marcacao.getUpdate(), UPSERT);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw e;
            }
            // Dois upserts simultâneos para o mesmo aluno: o outro criou o documento, agora basta atualizá-lo
            presencas.updateOne(marcacao.getFilter(), marcacao.getUpdate(), UPSERT);
        }
        return new PresencaAtualizada(sessaoId, alunoId, nome(alunoId), pedido.presente(), OrigemPresenca.MANUAL,
                agora.toEpochMilli(), sessoes.totalPresentes(sessaoId), totalMatriculados(turmaId));
    }

    /** Marca todos os matriculados com {@code presente} e o oposto nas exceções, em uma única escrita em lote. */
    public PresencaLoteOk marcarLote(UsuarioAutenticado professor, PresencaMarcarLote pedido) {
        Document sessao = sessaoClassicaDoProfessor(professor, pedido.sessaoId());
        String sessaoId = sessao.getObjectId("_id").toHexString();
        String turmaId = sessao.getString("turmaId");

        Set<String> matriculados = new HashSet<>();
        for (Document matricula : matriculas.find(eq("turmaId", turmaId)).projection(include("alunoId"))) {
            matriculados.add(matricula.getString("alunoId"));
        }
        Set<String> excecoes = new HashSet<>(pedido.excecoes() == null ? List.of() : pedido.excecoes());
        if (!matriculados.containsAll(excecoes)) {
            // Recusa antes de gravar: o lote é aplicado inteiro ou não é aplicado
            throw new ErroDeProtocolo(CodigoErro.ALUNO_NAO_MATRICULADO, "Há exceções que não são alunos da turma");
        }

        if (!matriculados.isEmpty()) {
            Instant agora = agora();
            List<WriteModel<Document>> escritas = new ArrayList<>(matriculados.size());
            for (String alunoId : matriculados) {
                boolean presente = excecoes.contains(alunoId) != pedido.presente();
                escritas.add(marcacao(sessaoId, turmaId, alunoId, presente, agora));
            }
            BulkWriteOptions opcoes = new BulkWriteOptions().ordered(false);
            try {
                presencas.bulkWrite(escritas, opcoes);
            } catch (MongoBulkWriteException e) {
                boolean soDuplicadas = e.getWriteErrors().stream()
                        .allMatch(erro -> ErrorCategory.fromErrorCode(erro.getCode()) == ErrorCategory.DUPLICATE_KEY);
                if (!soDuplicadas) {
                    throw e;
                }
                // Corrida com outra marcação: as escritas são idempotentes, então repetir o lote basta
                presencas.bulkWrite(escritas, opcoes);
            }
        }
        return new PresencaLoteOk(sessaoId, sessoes.totalPresentes(sessaoId), matriculados.size());
    }

    private Document sessaoClassicaDoProfessor(UsuarioAutenticado professor, String sessaoId) {
        Document sessao = sessoes.aberta(sessaoId);
        if (!professor.id().equals(sessao.getString("professorId"))) {
            throw new ErroDeProtocolo(CodigoErro.PERMISSAO_NEGADA, "Apenas o professor da sessão marca presenças");
        }
        if (!ModoChamada.CLASSICA.name().equals(sessao.getString("modo"))) {
            throw new ErroDeProtocolo(CodigoErro.MODO_INCOMPATIVEL, "A marcação manual só vale no modo clássico");
        }
        return sessao;
    }

    private static UpdateOneModel<Document> marcacao(String sessaoId, String turmaId, String alunoId,
                                                     boolean presente, Instant quando) {
        Bson filtro = and(eq("sessaoId", sessaoId), eq("alunoId", alunoId));
        Bson atualizacao = combine(
                set("turmaId", turmaId),
                set("presente", presente),
                set("origem", OrigemPresenca.MANUAL.name()),
                set("registradoEm", Date.from(quando)));
        return new UpdateOneModel<>(filtro, atualizacao, UPSERT);
    }

    private int totalMatriculados(String turmaId) {
        return (int) matriculas.countDocuments(eq("turmaId", turmaId));
    }

    private String nome(String alunoId) {
        if (!ObjectId.isValid(alunoId)) {
            return null;
        }
        Document aluno = usuarios.find(eq("_id", new ObjectId(alunoId))).projection(include("nome")).first();
        return aluno == null ? null : aluno.getString("nome");
    }

    /** O MongoDB guarda datas com precisão de milissegundos. */
    private Instant agora() {
        return relogio.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
