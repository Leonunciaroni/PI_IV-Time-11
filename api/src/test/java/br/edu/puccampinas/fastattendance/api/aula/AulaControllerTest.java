package br.edu.puccampinas.fastattendance.api.aula;

import br.edu.puccampinas.fastattendance.api.MongoTestcontainers;
import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.PresencaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.SessaoRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestcontainers.class)
class AulaControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TurmaRepository turmas;
    @Autowired MatriculaRepository matriculas;
    @Autowired SessaoRepository sessoes;
    @Autowired PresencaRepository presencas;
    @Autowired JwtService jwt;
    Usuario aluno;
    Usuario professor;
    Usuario outro;
    Turma turma;

    @BeforeEach
    void preparar() {
        presencas.deleteAll(); sessoes.deleteAll(); matriculas.deleteAll(); turmas.deleteAll(); usuarios.deleteAll();
        aluno = usuario("Ana", "ana@teste.br", Papel.ALUNO);
        professor = usuario("Professor", "prof@teste.br", Papel.PROFESSOR);
        outro = usuario("Outro professor", "outro@teste.br", Papel.PROFESSOR);
        turma = turma("A", professor.id());
        matriculas.insert(new Matricula(null, turma.id(), aluno.id(), null));
    }

    @Test
    void donoVeTodasAulasOrdenadasComModoETotalRealDePresentes() throws Exception {
        var primeira = aula(turma, ModoChamada.QRCODE, StatusSessao.ENCERRADA, 0);
        var segunda = aula(turma, ModoChamada.CLASSICA, StatusSessao.ABERTA, 1);
        presencas.insert(new Presenca(null, primeira.id(), turma.id(), aluno.id(), true,
                OrigemPresenca.MANUAL, null, null, null, Instant.now()));
        presencas.insert(new Presenca(null, segunda.id(), turma.id(), aluno.id(), false,
                OrigemPresenca.MANUAL, null, null, null, Instant.now()));
        mvc.perform(get("/api/turmas/{id}/aulas", turma.id()).header("Authorization", bearer(professor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(segunda.id()))
                .andExpect(jsonPath("$[0].modo").value("CLASSICA"))
                .andExpect(jsonPath("$[0].status").value("ABERTA"))
                .andExpect(jsonPath("$[0].encerradaEm").isEmpty())
                .andExpect(jsonPath("$[0].totalPresentes").value(0))
                .andExpect(jsonPath("$[1].totalPresentes").value(1))
                .andExpect(jsonPath("$[1].modo").value("QRCODE"))
                .andExpect(jsonPath("$[1].segredoTotp").doesNotExist());
    }

    @Test
    void protegeAulasDeOutrosProfessoresERecusaAlunos() throws Exception {
        mvc.perform(get("/api/turmas/{id}/aulas", turma.id()).header("Authorization", bearer(outro)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/turmas/inexistente/aulas").header("Authorization", bearer(professor)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/turmas/{id}/aulas", turma.id()).header("Authorization", bearer(aluno)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ativasIncluemTodosModosMasSomenteMatriculasDoAlunoESemSegredo() throws Exception {
        aula(turma, ModoChamada.CLASSICA, StatusSessao.ENCERRADA, 0);
        var qr = aula(turma, ModoChamada.QRCODE, StatusSessao.ABERTA, 1);
        var som = turma("Som", professor.id());
        matriculas.insert(new Matricula(null, som.id(), aluno.id(), null));
        var abertaSom = aula(som, ModoChamada.SOM, StatusSessao.ABERTA, 2);
        var manual = turma("Manual", professor.id());
        matriculas.insert(new Matricula(null, manual.id(), aluno.id(), null));
        aula(manual, ModoChamada.CLASSICA, StatusSessao.ABERTA, 0);
        aula(turma("Outra", outro.id()), ModoChamada.SOM, StatusSessao.ABERTA, 3);
        mvc.perform(get("/api/sessoes/ativas").header("Authorization", bearer(aluno)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].sessaoId").value(abertaSom.id()))
                .andExpect(jsonPath("$[0].modo").value("SOM"))
                .andExpect(jsonPath("$[0].disciplina").value("Disciplina"))
                .andExpect(jsonPath("$[0].segredoTotp").doesNotExist())
                .andExpect(jsonPath("$[1].sessaoId").value(qr.id()))
                .andExpect(jsonPath("$[2].modo").value("CLASSICA"));
    }

    @Test
    void listasVaziasEAutorizacao() throws Exception {
        mvc.perform(get("/api/turmas/{id}/aulas", turma.id()).header("Authorization", bearer(professor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/sessoes/ativas").header("Authorization", bearer(aluno)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        matriculas.deleteAll();
        aula(turma, ModoChamada.SOM, StatusSessao.ABERTA, 1);
        mvc.perform(get("/api/sessoes/ativas").header("Authorization", bearer(aluno)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/sessoes/ativas").header("Authorization", bearer(professor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sessoes/ativas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/turmas/{id}/aulas", turma.id())).andExpect(status().isUnauthorized());
    }

    Usuario usuario(String nome, String email, Papel papel) {
        return usuarios.insert(new Usuario(null, nome, email, "hash-nao-usado", papel, "123", null));
    }
    String bearer(Usuario usuario) { return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token(); }
    Turma turma(String nome, String professorId) {
        return turmas.insert(new Turma(null, nome, "Disciplina", "Seg 19:00", professorId, null));
    }
    Sessao aula(Turma daTurma, ModoChamada modo, StatusSessao status, int dia) {
        var inicio = Instant.parse("2026-10-01T22:00:00Z").plusSeconds(dia * 86400L);
        return sessoes.insert(new Sessao(null, daTurma.id(), daTurma.professorId(), modo, null, "privado", status,
                inicio, status == StatusSessao.ENCERRADA ? inicio.plusSeconds(3600) : null, null));
    }
}
