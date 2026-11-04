package br.edu.puccampinas.fastattendance.sessao.chamada;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.Envelope;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.MotivoEncerramento;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresencaAtualizada;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresenteResumo;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.SessaoEncerrada;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.SessaoEstado;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import br.edu.puccampinas.fastattendance.sessao.protocolo.ErroDeProtocolo;
import br.edu.puccampinas.fastattendance.sessao.servidor.Conexao;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.include;

/**
 * Painel ao vivo do professor (docs/protocolo-v1.md, seções 4.5 e 5.2).
 * <p>
 * Cada réplica guarda, em memória, as conexões que acompanham cada sessão. Os eventos não são
 * enviados pelo roteador: chegam pelos Change Streams do MongoDB ({@link ObservadorDeMudancas}),
 * de modo que uma presença gravada em qualquer réplica alcança os professores conectados aqui.
 */
public final class PainelAoVivo {

    private final ServicoDeSessoes sessoes;
    private final MongoCollection<Document> matriculas;
    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> presencas;
    private final Map<String, Set<Conexao>> acompanhantes = new ConcurrentHashMap<>();

    public PainelAoVivo(MongoDatabase banco, ServicoDeSessoes sessoes) {
        this.sessoes = sessoes;
        this.matriculas = banco.getCollection(Colecoes.MATRICULAS);
        this.usuarios = banco.getCollection(Colecoes.USUARIOS);
        this.presencas = banco.getCollection(Colecoes.PRESENCAS);
    }

    /**
     * {@code sessao.acompanhar}: registra a conexão como acompanhante e devolve o estado atual.
     * <p>
     * O registro vem antes da leitura do estado: um registro gravado entre as duas operações pode
     * aparecer no estado e também no evento, mas nunca se perde.
     *
     * @throws ErroDeProtocolo {@code SESSAO_NAO_ENCONTRADA}, {@code SESSAO_ENCERRADA} ou
     *                         {@code PERMISSAO_NEGADA} para quem não é o professor da sessão
     */
    public SessaoEstado acompanhar(Conexao conexao, UsuarioAutenticado professor, String sessaoId) {
        if (sessaoId == null || sessaoId.isBlank()) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Informe sessaoId");
        }
        Document sessao = sessoes.aberta(sessaoId.strip());
        if (professor.papel() != Papel.PROFESSOR || !professor.id().equals(sessao.getString("professorId"))) {
            throw new ErroDeProtocolo(CodigoErro.PERMISSAO_NEGADA, "Apenas o professor da sessão a acompanha");
        }
        registrar(conexao, sessao.getObjectId("_id").toHexString());
        return estado(sessao);
    }

    /** Chamado pelo observador para cada presença gravada, em qualquer réplica. */
    void presencaGravada(Document presenca) {
        String sessaoId = presenca.getString("sessaoId");
        Set<Conexao> destinos = acompanhantes.getOrDefault(sessaoId, Set.of());
        if (destinos.isEmpty()) {
            return;
        }
        String alunoId = presenca.getString("alunoId");
        PresencaAtualizada dados = new PresencaAtualizada(
                sessaoId,
                alunoId,
                nome(alunoId),
                presenca.getBoolean("presente", false),
                OrigemPresenca.valueOf(presenca.getString("origem")),
                presenca.getDate("registradoEm").getTime(),
                sessoes.totalPresentes(sessaoId),
                totalMatriculados(presenca.getString("turmaId")));
        enviar(destinos, TipoMensagem.PRESENCA_ATUALIZADA, dados);
    }

    /** Chamado pelo observador quando a sessão passa a {@code ENCERRADA}. A sessão deixa de ter acompanhantes. */
    void sessaoEncerrada(Document sessao) {
        String sessaoId = sessao.getObjectId("_id").toHexString();
        Set<Conexao> destinos = acompanhantes.remove(sessaoId);
        if (destinos == null || destinos.isEmpty()) {
            return;
        }
        SessaoEncerrada dados = new SessaoEncerrada(
                sessaoId,
                MotivoEncerramento.valueOf(sessao.getString("motivoEncerramento")),
                sessao.getDate("encerradaEm").getTime(),
                sessoes.totalPresentes(sessaoId));
        enviar(destinos, TipoMensagem.SESSAO_ENCERRADA, dados);
    }

    private void registrar(Conexao conexao, String sessaoId) {
        acompanhantes.computeIfAbsent(sessaoId, id -> ConcurrentHashMap.newKeySet()).add(conexao);
        conexao.aoFechar(() -> desregistrar(conexao, sessaoId));
    }

    private void desregistrar(Conexao conexao, String sessaoId) {
        acompanhantes.computeIfPresent(sessaoId, (id, conjunto) -> {
            conjunto.remove(conexao);
            return conjunto.isEmpty() ? null : conjunto;
        });
    }

    private void enviar(Set<Conexao> destinos, TipoMensagem tipo, Object dados) {
        Envelope<?> evento = Envelope.evento(tipo, dados);
        for (Conexao destino : destinos) {
            try {
                destino.enviar(evento);
            } catch (RuntimeException e) {
                // Uma conexão com falha não pode impedir a entrega às demais
                System.getLogger(PainelAoVivo.class.getName())
                        .log(System.Logger.Level.WARNING, "Falha ao enviar evento a um acompanhante", e);
            }
        }
    }

    private SessaoEstado estado(Document sessao) {
        String sessaoId = sessao.getObjectId("_id").toHexString();
        List<PresenteResumo> presentes = new ArrayList<>();
        for (Document presenca : presencas.find(and(eq("sessaoId", sessaoId), eq("presente", true)))
                .sort(Sorts.ascending("registradoEm"))) {
            String alunoId = presenca.getString("alunoId");
            presentes.add(new PresenteResumo(alunoId, nome(alunoId),
                    OrigemPresenca.valueOf(presenca.getString("origem")),
                    presenca.getDate("registradoEm").getTime()));
        }
        return new SessaoEstado(sessaoId, ModoChamada.valueOf(sessao.getString("modo")),
                sessao.getDate("abertaEm").getTime(), presentes,
                totalMatriculados(sessao.getString("turmaId")));
    }

    private int totalMatriculados(String turmaId) {
        return (int) matriculas.countDocuments(eq("turmaId", turmaId));
    }

    private String nome(String alunoId) {
        if (alunoId == null || !ObjectId.isValid(alunoId)) {
            return null;
        }
        Document aluno = usuarios.find(eq("_id", new ObjectId(alunoId))).projection(include("nome")).first();
        return aluno == null ? null : aluno.getString("nome");
    }
}
