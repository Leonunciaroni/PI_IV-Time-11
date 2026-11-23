// Guilherme Preventi Correia
// RA: 25001694

package br.edu.puccampinas.fastattendance.api.aluno;

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
class InicioAlunoControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TurmaRepository turmas;
    @Autowired MatriculaRepository matriculas;
    @Autowired SessaoRepository sessoes;
    @Autowired PresencaRepository presencas;
    @Autowired JwtService jwt;
    Usuario aluno;
    Usuario professor;

    @BeforeEach
    void preparar() {
        presencas.deleteAll(); sessoes.deleteAll(); matriculas.deleteAll(); turmas.deleteAll(); usuarios.deleteAll();
        aluno = usuarios.insert(new Usuario(null, "Ana", "ana@teste.br", "hash", Papel.ALUNO, "1", null));
        professor = usuarios.insert(new Usuario(null, "Professor", "prof@teste.br", "hash", Papel.PROFESSOR, "P1", null));
    }

    @Test
    void consultaRestritaAoAlunoAutenticado() throws Exception {
        mvc.perform(get("/api/aluno/turmas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/aluno/turmas").header("Authorization", bearer(professor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semMatriculasRetornaListaVazia() throws Exception {
        turma("Turma de outro aluno", "Matemática", false);
        mvc.perform(get("/api/aluno/turmas").header("Authorization", bearer(aluno)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listaSomenteSuasTurmasOrdenadasComFrequenciaEChamadaSemSegredo() throws Exception {
        var b = turma("B", "Zoologia", true);
        var a = turma("A", "Algoritmos", true);
        turma("Invisível", "Disciplina de outro aluno", false);
        var encerrada1 = aula(a, StatusSessao.ENCERRADA, ModoChamada.CLASSICA, 0);
        aula(a, StatusSessao.ENCERRADA, ModoChamada.SOM, 1);
        var aberta = aula(a, StatusSessao.ABERTA, ModoChamada.QRCODE, 2);
        presencas.insert(new Presenca(null, encerrada1.id(), a.id(), aluno.id(), true, OrigemPresenca.MANUAL,
                null, null, null, Instant.now()));
        // A presença da aula aberta não altera a frequência das aulas já encerradas.
        presencas.insert(new Presenca(null, aberta.id(), a.id(), aluno.id(), true, OrigemPresenca.MANUAL,
                null, null, null, Instant.now()));
        mvc.perform(get("/api/aluno/turmas").header("Authorization", bearer(aluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(a.id()))
                .andExpect(jsonPath("$[0].aulasDadas").value(2))
                .andExpect(jsonPath("$[0].presencas").value(1))
                .andExpect(jsonPath("$[0].percentual").value(50.0))
                .andExpect(jsonPath("$[0].abaixoDoMinimo").value(true))
                .andExpect(jsonPath("$[0].chamadaAberta.modo").value("QRCODE"))
                .andExpect(jsonPath("$[0].chamadaAberta.id").value(aberta.id()))
                .andExpect(jsonPath("$[0].chamadaAberta.segredoTotp").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(b.id()))
                .andExpect(jsonPath("$[1].aulasDadas").value(0))
                .andExpect(jsonPath("$[1].abaixoDoMinimo").value(false))
                .andExpect(jsonPath("$[1].chamadaAberta").isEmpty());
    }

    @Test
    void apresentaSomClassicaEEncerramentoDaChamada() throws Exception {
        var som = turma("A", "Algoritmos", true);
        var classica = turma("B", "Biologia", true);
        var chamada = aula(som, StatusSessao.ABERTA, ModoChamada.SOM, 0);
        aula(classica, StatusSessao.ABERTA, ModoChamada.CLASSICA, 0);
        mvc.perform(get("/api/aluno/turmas").header("Authorization", bearer(aluno)))
                .andExpect(jsonPath("$[0].chamadaAberta.modo").value("SOM"))
                .andExpect(jsonPath("$[1].chamadaAberta.modo").value("CLASSICA"));
        sessoes.save(new Sessao(chamada.id(), som.id(), professor.id(), chamada.modo(), null, "segredo",
                StatusSessao.ENCERRADA, chamada.abertaEm(), Instant.now(), null));
        mvc.perform(get("/api/aluno/turmas").header("Authorization", bearer(aluno)))
                .andExpect(jsonPath("$[0].chamadaAberta").isEmpty())
                .andExpect(jsonPath("$[0].aulasDadas").value(1));
    }

    String bearer(Usuario usuario) { return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token(); }

    Turma turma(String nome, String disciplina, boolean matricular) {
        var turma = turmas.insert(new Turma(null, nome, disciplina, "Seg 19:00", professor.id(), null));
        if (matricular) matriculas.insert(new Matricula(null, turma.id(), aluno.id(), null));
        return turma;
    }

    Sessao aula(Turma turma, StatusSessao status, ModoChamada modo, int dia) {
        var inicio = Instant.parse("2026-10-01T22:00:00Z").plusSeconds(dia * 86400L);
        return sessoes.insert(new Sessao(null, turma.id(), professor.id(), modo, null, "segredo", status,
                inicio, status == StatusSessao.ENCERRADA ? inicio.plusSeconds(3600) : null, null));
    }
}
