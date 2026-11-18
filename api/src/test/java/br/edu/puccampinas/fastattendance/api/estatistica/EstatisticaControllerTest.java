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
class EstatisticaControllerTest {

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
    }

    @Test
    void calculaEstatisticasDaTurma() throws Exception {
        matricular(ana, bruno);
        Sessao aula1 = aula(ModoChamada.CLASSICA, StatusSessao.ENCERRADA, 0);
        Sessao aula2 = aula(ModoChamada.QRCODE, StatusSessao.ENCERRADA, 1);
        // Aula 1: os dois presentes. Aula 2: só a Ana.
        presenca(aula1, ana, true);
        presenca(aula1, bruno, true);
        presenca(aula2, ana, true);

        consultar(professor, turma.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aulasDadas").value(2))
                .andExpect(jsonPath("$.totalAlunos").value(2))
                .andExpect(jsonPath("$.frequenciaMedia").value(75.0))
                .andExpect(jsonPath("$.minimo").value(75))
                .andExpect(jsonPath("$.aulas[0].sessaoId").value(aula1.id()))
                .andExpect(jsonPath("$.aulas[0].percentual").value(100.0))
                .andExpect(jsonPath("$.aulas[1].modo").value("QRCODE"))
                .andExpect(jsonPath("$.aulas[1].presentes").value(1))
                .andExpect(jsonPath("$.aulas[1].matriculados").value(2))
                .andExpect(jsonPath("$.aulas[1].percentual").value(50.0))
                .andExpect(jsonPath("$.alunos.length()").value(2))
                .andExpect(jsonPath("$.alunos[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$.alunos[0].presencas").value(2))
                .andExpect(jsonPath("$.alunos[0].aulas").value(2))
                .andExpect(jsonPath("$.alunos[0].percentual").value(100.0))
                .andExpect(jsonPath("$.alunos[1].nome").value("Bruno Lima"))
                .andExpect(jsonPath("$.alunos[1].percentual").value(50.0))
                .andExpect(jsonPath("$.abaixoDoMinimo.length()").value(1))
                .andExpect(jsonPath("$.abaixoDoMinimo[0].nome").value("Bruno Lima"))
                .andExpect(jsonPath("$.abaixoDoMinimo[0].matricula").value("24005678"))
                .andExpect(jsonPath("$.abaixoDoMinimo[0].presencas").value(1))
                .andExpect(jsonPath("$.abaixoDoMinimo[0].aulas").value(2))
                .andExpect(jsonPath("$.abaixoDoMinimo[0].percentual").value(50.0))
                .andExpect(jsonPath("$.usoPorModo.CLASSICA").value(1))
                .andExpect(jsonPath("$.usoPorModo.QRCODE").value(1))
                .andExpect(jsonPath("$.usoPorModo.SOM").value(0));
    }

    @Test
    void turmaSemAulasDevolveZeros() throws Exception {
        matricular(ana);

        consultar(professor, turma.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aulasDadas").value(0))
                .andExpect(jsonPath("$.totalAlunos").value(1))
                .andExpect(jsonPath("$.frequenciaMedia").value(0.0))
                .andExpect(jsonPath("$.aulas.length()").value(0))
                .andExpect(jsonPath("$.alunos.length()").value(1))
                .andExpect(jsonPath("$.alunos[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$.alunos[0].aulas").value(0))
                .andExpect(jsonPath("$.abaixoDoMinimo.length()").value(0));
    }

    @Test
    void ignoraAulaAindaAberta() throws Exception {
        matricular(ana);
        Sessao encerrada = aula(ModoChamada.CLASSICA, StatusSessao.ENCERRADA, 0);
        Sessao aberta = aula(ModoChamada.SOM, StatusSessao.ABERTA, 1);
        presenca(encerrada, ana, true);
        presenca(aberta, ana, true);

        consultar(professor, turma.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aulasDadas").value(1))
                .andExpect(jsonPath("$.usoPorModo.SOM").value(0));
    }

    @Test
    void presencaDesmarcadaNaoContaComoPresente() throws Exception {
        matricular(ana);
        Sessao aula = aula(ModoChamada.CLASSICA, StatusSessao.ENCERRADA, 0);
        presenca(aula, ana, false);

        consultar(professor, turma.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aulas[0].presentes").value(0))
                .andExpect(jsonPath("$.alunos[0].presencas").value(0))
                .andExpect(jsonPath("$.alunos[0].percentual").value(0.0))
                .andExpect(jsonPath("$.abaixoDoMinimo[0].nome").value("Ana Souza"));
    }

    @Test
    void turmaDeOutroProfessorResponde404() throws Exception {
        consultar(outroProfessor, turma.id()).andExpect(status().isNotFound());
    }

    @Test
    void turmaInexistenteResponde404() throws Exception {
        consultar(professor, "000000000000000000000000").andExpect(status().isNotFound());
    }

    @Test
    void alunoNaoAcessaAsEstatisticas() throws Exception {
        consultar(ana, turma.id()).andExpect(status().isForbidden());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(get("/api/turmas/{id}/estatisticas", turma.id())).andExpect(status().isUnauthorized());
    }

    // ---- Apoio ----

    Usuario usuario(String nome, String email, Papel papel, String matricula) {
        return usuarios.insert(new Usuario(null, nome, email, "hash-nao-usado", papel, matricula, null));
    }

    String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token();
    }

    ResultActions consultar(Usuario usuario, String turmaId) throws Exception {
        return mvc.perform(get("/api/turmas/{id}/estatisticas", turmaId).header("Authorization", bearer(usuario)));
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
