/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.sessao.totp.ValidadorTotp;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.JsonNode;

/**
 * Base dos testes que usam dados: o MongoDB de teste (Testcontainers, replica set) com uma turma, um
 * professor, uma sala e três alunos, dois deles matriculados. Requer Docker.
 */
abstract class CenarioDePonte extends CenarioSemBanco {

    /** Sala de referência: coordenadas do exemplo da seção 9 do protocolo, raio de 50 m. */
    static final double SALA_LAT = -22.8339;
    static final double SALA_LON = -47.0525;
    static final double RAIO_SALA = 50;

    /** Sessão aberta: identificador e segredo TOTP (para gerar tokens nos testes). */
    record Sessao(String id, String segredo) {
    }

    @Override
    protected String mongoUri() {
        return MongoDeTeste.uri();
    }

    @BeforeEach
    void semear() {
        MongoDeTeste.limpar();
        inserir("turmas", turmaId, new Document("nome", "Turma A").append("professorId", professorId));
        inserir("salas", salaId, new Document("nome", "H15 - Sala 203")
                .append("latitude", SALA_LAT).append("longitude", SALA_LON).append("raioMetros", RAIO_SALA));
        inserir("usuarios", ana, new Document("nome", "Ana Souza").append("papel", "ALUNO"));
        inserir("usuarios", bruno, new Document("nome", "Bruno Lima").append("papel", "ALUNO"));
        inserir("usuarios", alunoDeOutraTurma, new Document("nome", "Diego Alves").append("papel", "ALUNO"));
        for (String alunoId : new String[]{ana, bruno}) {
            inserir("matriculas", novoId(), new Document("turmaId", turmaId).append("alunoId", alunoId));
        }
    }

    /** Abre uma sessão no modo informado; devolve o id e o segredo da resposta {@code sessao.aberta}. */
    protected Sessao abrirSessao(ClienteNavegador professor, String modo, String salaIdOpcional) throws Exception {
        String sala = salaIdOpcional == null ? "" : ",\"salaId\":\"" + salaIdOpcional + "\"";
        professor.enviar(frame("sessao.abrir", id("abrir"),
                "{\"turmaId\":\"" + turmaId + "\",\"modo\":\"" + modo + "\"" + sala + "}"));
        JsonNode dados = professor.proximo("sessao.aberta").get("dados");
        return new Sessao(dados.get("sessaoId").stringValue(), dados.get("totp").get("segredo").stringValue());
    }

    protected static String tokenAtual(String segredo) {
        return ValidadorTotp.gerar(segredo, instanteAgora());
    }

    protected static long instanteAgora() {
        return System.currentTimeMillis() / 1000;
    }

    private static void inserir(String colecao, String hex, Document documento) {
        MongoDeTeste.banco().getCollection(colecao)
                .insertOne(documento.append("_id", new org.bson.types.ObjectId(hex)));
    }
}
