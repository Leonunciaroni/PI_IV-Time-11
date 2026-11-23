/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.config;

import br.edu.puccampinas.fastattendance.api.MongoTestcontainers;
import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MongoTestcontainers.class)
class IndicesMongoTest {

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void limpar() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Presenca.class);
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Sessao.class);
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Usuario.class);
    }

    @Test
    void rejeitaSegundaPresencaDoMesmoAlunoNaMesmaSessao() {
        mongo.insert(presenca("aluno-1", "sessao-1", "disp-1"));

        assertThrows(DuplicateKeyException.class,
                () -> mongo.insert(presenca("aluno-1", "sessao-1", "disp-2")));
    }

    @Test
    void aceitaMesmoAlunoEmSessoesDiferentes() {
        mongo.insert(presenca("aluno-1", "sessao-1", "disp-1"));

        assertDoesNotThrow(() -> mongo.insert(presenca("aluno-1", "sessao-2", "disp-1")));
    }

    @Test
    void rejeitaMesmoDispositivoParaDoisAlunosNaMesmaSessao() {
        mongo.insert(presenca("aluno-1", "sessao-1", "disp-1"));

        assertThrows(DuplicateKeyException.class,
                () -> mongo.insert(presenca("aluno-2", "sessao-1", "disp-1")));
    }

    @Test
    void presencasManuaisSemDispositivoNaoConflitam() {
        mongo.insert(presenca("aluno-1", "sessao-1", null));

        assertDoesNotThrow(() -> mongo.insert(presenca("aluno-2", "sessao-1", null)));
    }

    @Test
    void rejeitaEmailDuplicado() {
        mongo.insert(usuario("ana@puc.br"));

        assertThrows(DuplicateKeyException.class, () -> mongo.insert(usuario("ana@puc.br")));
    }

    @Test
    void permiteApenasUmaSessaoAbertaPorTurma() {
        mongo.insert(sessao("turma-1", StatusSessao.ENCERRADA));
        mongo.insert(sessao("turma-1", StatusSessao.ABERTA));

        assertThrows(DuplicateKeyException.class,
                () -> mongo.insert(sessao("turma-1", StatusSessao.ABERTA)));
    }

    private static Presenca presenca(String alunoId, String sessaoId, String dispositivoId) {
        OrigemPresenca origem = dispositivoId == null ? OrigemPresenca.MANUAL : OrigemPresenca.QRCODE;
        return new Presenca(null, sessaoId, "turma-1", alunoId, true, origem, dispositivoId,
                null, null, Instant.now());
    }

    private static Usuario usuario(String email) {
        return new Usuario(null, "Ana", email, "hash", Papel.ALUNO, "24001234", null);
    }

    private static Sessao sessao(String turmaId, StatusSessao status) {
        return new Sessao(null, turmaId, "prof-1", ModoChamada.QRCODE, null, "SEGREDO", status,
                Instant.now(), null, null);
    }
}
