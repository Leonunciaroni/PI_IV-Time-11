/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestcontainers.class)
class HistoricoControllerTest {

    static final Instant INICIO = Instant.parse("2026-10-05T22:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    TurmaRepository turmas;

    @Autowired
    MatriculaRepository matriculas;

    @Autowired
    SessaoRepository sessoes;

    @Autowired
    PresencaRepository presencas;

    @Autowired
    JwtService jwt;

    Usuario professor;
    Usuario outroProfessor;
    Usuario ana;
    Usuario bruno;
    Turma turma;

    @BeforeEach
    void prepararDados() {
        presencas.deleteAll();
        sessoes.deleteAll();
        matriculas.deleteAll();
        turmas.deleteAll();
        usuarios.deleteAll();
        professor = usuario("Prof. Matheus", "matheus@puc.br", Papel.PROFESSOR, "P0001");
        outroProfessor = usuario("Profa. Clara", "clara@puc.br", Papel.PROFESSOR, "P0002");
        ana = usuario("Ana Souza", "ana@puc.br", Papel.ALUNO, "24001234");
        bruno = usuario("Bruno Lima", "bruno@puc.br", Papel.ALUNO, "24005678");
        turma = turmas.insert(new Turma(null, "Turma A", "Engenharia de Software I", "Seg 19:00",
                professor.id(), null));
        matricular(ana, bruno);
    }

    @Test
    void professorVeOHistoricoDeUmAlunoDaTurma() throws Exception {
        Sessao aula1 = aula(ModoChamada.CLASSICA, StatusSessao.ENCERRADA, 0);
        Sessao aula2 = aula(ModoChamada.QRCODE, StatusSessao.ENCERRADA, 1);
        presenca(aula1, bruno, true);

        consultar(professor, turma.id(), bruno.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turmaId").value(turma.id()))
                .andExpect(jsonPath("$.alunoId").value(bruno.id()))
                .andExpect(jsonPath("$.nome").value("Bruno Lima"))
                .andExpect(jsonPath("$.matricula").value("24005678"))
                .andExpect(jsonPath("$.aulasDadas").value(2))
                .andExpect(jsonPath("$.presencas").value(1))
                .andExpect(jsonPath("$.percentual").value(50.0))
                .andExpect(jsonPath("$.minimo").value(75))
                .andExpect(jsonPath("$.abaixoDoMinimo").value(true))
                .andExpect(jsonPath("$.aulas[0].sessaoId").value(aula1.id()))
                .andExpect(jsonPath("$.aulas[0].presente").value(true))
                .andExpect(jsonPath("$.aulas[1].sessaoId").value(aula2.id()))
                .andExpect(jsonPath("$.aulas[1].modo").value("QRCODE"))
                .andExpect(jsonPath("$.aulas[1].presente").value(false));
    }

    @Test
    void alunoVeOProprioHistorico() throws Exception {
        Sessao aula = aula(ModoChamada.SOM, StatusSessao.ENCERRADA, 0);
        presenca(aula, ana, true);

        consultar(ana, turma.id(), ana.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.percentual").value(100.0))
                .andExpect(jsonPath("$.abaixoDoMinimo").value(false));
    }

    @Test
    void alunoNaoVeOHistoricoDeOutroAluno() throws Exception {
        consultar(ana, turma.id(), bruno.id())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Você só pode ver o seu próprio histórico."));
    }

    @Test
    void ignoraAulaAindaAberta() throws Exception {
        Sessao encerrada = aula(ModoChamada.CLASSICA, StatusSessao.ENCERRADA, 0);
        Sessao aberta = aula(ModoChamada.SOM, StatusSessao.ABERTA, 1);
        presenca(encerrada, ana, true);
        presenca(aberta, ana, true);

        consultar(professor, turma.id(), ana.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aulasDadas").value(1))
                .andExpect(jsonPath("$.aulas.length()").value(1));
    }

    @Test
    void presencaDesmarcadaApareceComoAusente() throws Exception {
        Sessao aula = aula(ModoChamada.CLASSICA, StatusSessao.ENCERRADA, 0);
        presenca(aula, ana, false);

        consultar(professor, turma.id(), ana.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aulas[0].presente").value(false))
                .andExpect(jsonPath("$.presencas").value(0));
    }

    @Test
    void turmaSemAulasDevolveHistoricoVazio() throws Exception {
        consultar(professor, turma.id(), ana.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aulasDadas").value(0))
                .andExpect(jsonPath("$.percentual").value(0.0))
                .andExpect(jsonPath("$.abaixoDoMinimo").value(false))
                .andExpect(jsonPath("$.aulas.length()").value(0));
    }

    @Test
    void professorDeOutraTurmaResponde404() throws Exception {
        consultar(outroProfessor, turma.id(), ana.id()).andExpect(status().isNotFound());
    }

    @Test
    void alunoNaoMatriculadoNaTurmaResponde404() throws Exception {
        Usuario carla = usuario("Carla Dias", "carla@puc.br", Papel.ALUNO, "24009999");

        consultar(professor, turma.id(), carla.id()).andExpect(status().isNotFound());
        consultar(carla, turma.id(), carla.id()).andExpect(status().isNotFound());
    }

    @Test
    void turmaInexistenteResponde404() throws Exception {
        consultar(professor, "000000000000000000000000", ana.id()).andExpect(status().isNotFound());
        consultar(ana, "000000000000000000000000", ana.id()).andExpect(status().isNotFound());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(get("/api/turmas/{id}/frequencia/{alunoId}", turma.id(), ana.id()))
                .andExpect(status().isUnauthorized());
    }

    // ---- Apoio ----

    Usuario usuario(String nome, String email, Papel papel, String matricula) {
        return usuarios.insert(new Usuario(null, nome, email, "hash-nao-usado", papel, matricula, null));
    }

    String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token();
    }

    ResultActions consultar(Usuario quem, String turmaId, String alunoId) throws Exception {
        return mvc.perform(get("/api/turmas/{id}/frequencia/{alunoId}", turmaId, alunoId)
                .header("Authorization", bearer(quem)));
    }

    void matricular(Usuario... alunos) {
        for (Usuario aluno : alunos) {
            matriculas.insert(new Matricula(null, turma.id(), aluno.id(), null));
        }
    }

    Sessao aula(ModoChamada modo, StatusSessao status, int diasDepois) {
        Instant abertura = INICIO.plusSeconds(diasDepois * 86400L);
        Instant encerramento = status == StatusSessao.ENCERRADA ? abertura.plusSeconds(3600) : null;
        return sessoes.insert(new Sessao(null, turma.id(), professor.id(), modo, null, "segredo", status,
                abertura, encerramento, null));
    }

    void presenca(Sessao sessao, Usuario aluno, boolean presente) {
        presencas.insert(new Presenca(null, sessao.id(), turma.id(), aluno.id(), presente, OrigemPresenca.MANUAL,
                null, null, null, INICIO));
    }
}
