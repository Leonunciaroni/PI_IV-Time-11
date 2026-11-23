/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Base dos testes de integração que não precisam de banco: um servidor de sessão no modelo, uma ponte
 * ligada a ele, e identificadores aleatórios. Os testes conversam com a ponte pelo WebSocket, como os apps.
 * Quando o teste precisa de dados (turmas, alunos, sessões), use {@link CenarioDePonte}.
 */
abstract class CenarioSemBanco {

    static final String SEGREDO = ServidorDeTeste.SEGREDO;
    static final JsonMapper JSON = JsonMapper.builder().build();

    final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(1));
    private final List<AutoCloseable> recursos = new ArrayList<>();
    protected ServidorDeTeste servidor;
    protected PonteDeTeste ponte;

    /** Identificadores de professor, turma, sala e alunos: ObjectId em hexadecimal. */
    protected String professorId = novoId();
    protected String turmaId = novoId();
    protected String salaId = novoId();
    protected String ana = novoId();
    protected String bruno = novoId();
    protected String alunoDeOutraTurma = novoId();

    private int sequencia;

    @BeforeEach
    void preparar() throws Exception {
        servidor = new ServidorDeTeste(mongoUri(), Duration.ofSeconds(5), Duration.ofSeconds(60));
        ponte = new PonteDeTeste(servidor.porta(), Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @AfterEach
    void parar() throws Exception {
        for (AutoCloseable recurso : recursos) {
            recurso.close();
        }
        ponte.close();
        servidor.close();
    }

    /** Banco usado pelo servidor de teste. Esta base não usa o banco. */
    protected String mongoUri() {
        return ServidorDeTeste.MONGO_INDISPONIVEL;
    }

    static String novoId() {
        return new ObjectId().toHexString();
    }

    /** Conexão WebSocket sem autenticação. Fechada no fim do teste. */
    protected ClienteNavegador conectar() throws Exception {
        ClienteNavegador cliente = new ClienteNavegador(ponte.porta());
        recursos.add(cliente);
        return cliente;
    }

    /** Conexão autenticada com um JWT emitido pela API (aqui, com o mesmo segredo). */
    protected ClienteNavegador autenticado(String usuarioId, Papel papel) throws Exception {
        ClienteNavegador cliente = conectar();
        String token = jwt.emitir(usuarioId, "Usuário", papel).token();
        cliente.enviar(frame("auth", id("auth"), "{\"token\":\"" + token + "\"}"));
        cliente.proximo("auth.ok");
        return cliente;
    }

    protected ClienteNavegador professor() throws Exception {
        return autenticado(professorId, Papel.PROFESSOR);
    }

    protected ClienteNavegador alunoConectado(String alunoId) throws Exception {
        return autenticado(alunoId, Papel.ALUNO);
    }

    /** Identificador curto e único dentro do teste. */
    protected String id(String prefixo) {
        return prefixo + (++sequencia);
    }

    /** Envelope do protocolo v1 (docs/protocolo-v1.md, seção 2), com {@code dadosJson} já serializado. */
    protected static String frame(String tipo, String id, String dadosJson) {
        String idJson = id == null ? "" : ",\"id\":\"" + id + "\"";
        return "{\"v\":1,\"tipo\":\"" + tipo + "\"" + idJson + ",\"ts\":" + System.currentTimeMillis()
                + ",\"dados\":" + dadosJson + "}";
    }
}
