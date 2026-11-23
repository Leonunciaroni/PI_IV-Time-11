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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestcontainers.class)
class ExportacaoControllerTest {

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
        turma = turmas.insert(new Turma(null, "Turma Ágil 2026", "Engenharia de Software I", "Seg 19:00",
                professor.id(), null));
        matriculas.insert(new Matricula(null, turma.id(), ana.id(), null));
        matriculas.insert(new Matricula(null, turma.id(), bruno.id(), null));
    }

    @Test
    void baixaOConsolidadoDaTurma() throws Exception {
        Sessao aula1 = aula(StatusSessao.ENCERRADA, 0);
        Sessao aula2 = aula(StatusSessao.ENCERRADA, 1);
        aula(StatusSessao.ABERTA, 2);
        presenca(aula1, ana);
        presenca(aula2, ana);
        presenca(aula1, bruno);

        MvcResult resultado = mvc.perform(get("/api/turmas/{id}/exportacao/consolidado", turma.id())
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        startsWith("attachment; filename=\"frequencia-turma-agil-2026.csv\"")))
                .andReturn();

        String corpo = new String(resultado.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertEquals("﻿Nome;Matrícula;Presenças;Aulas;Frequência (%);Situação\r\n"
                + "Ana Souza;24001234;2;2;100,0;Regular\r\n"
                + "Bruno Lima;24005678;1;2;50,0;Abaixo de 75%\r\n", corpo);
    }

    @Test
    void baixaAChamadaDeUmaAula() throws Exception {
        Sessao aula = aula(StatusSessao.ENCERRADA, 0);
        presenca(aula, ana);

        MvcResult resultado = mvc.perform(get("/api/turmas/{id}/exportacao/aulas/{aula}", turma.id(), aula.id())
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        startsWith("attachment; filename=\"chamada-turma-agil-2026-2026-10-05.csv\"")))
                .andReturn();

        String corpo = new String(resultado.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(corpo.contains("Ana Souza;24001234;Presente;MANUAL;05/10/2026 19:00\r\n"));
        assertTrue(corpo.contains("Bruno Lima;24005678;Ausente;;\r\n"));
    }

    @Test
    void aulaDeOutraTurmaRespondeComoInexistente() throws Exception {
        Turma outra = turmas.insert(new Turma(null, "Outra", "Banco de Dados", "Ter 19:00", professor.id(), null));
        Sessao aulaDaOutra = sessoes.insert(new Sessao(null, outra.id(), professor.id(), ModoChamada.CLASSICA, null,
                "segredo", StatusSessao.ENCERRADA, INICIO, INICIO.plusSeconds(3600), null));

        mvc.perform(get("/api/turmas/{id}/exportacao/aulas/{aula}", turma.id(), aulaDaOutra.id())
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/turmas/{id}/exportacao/aulas/nao-existe", turma.id())
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void turmaDeOutroProfessorRespondeComoInexistente() throws Exception {
        mvc.perform(get("/api/turmas/{id}/exportacao/consolidado", turma.id())
                        .header("Authorization", bearer(outroProfessor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void alunoNaoPodeExportar() throws Exception {
        mvc.perform(get("/api/turmas/{id}/exportacao/consolidado", turma.id())
                        .header("Authorization", bearer(ana)))
                .andExpect(status().isForbidden());
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/turmas/{id}/exportacao/consolidado", turma.id()))
                .andExpect(status().isUnauthorized());
    }

    // ---- Apoio ----

    Usuario usuario(String nome, String email, Papel papel, String matricula) {
        return usuarios.insert(new Usuario(null, nome, email, "hash-nao-usado", papel, matricula, null));
    }

    String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token();
    }

    Sessao aula(StatusSessao status, int diasDepois) {
        Instant abertura = INICIO.plusSeconds(diasDepois * 86400L);
        Instant encerramento = status == StatusSessao.ENCERRADA ? abertura.plusSeconds(3600) : null;
        return sessoes.insert(new Sessao(null, turma.id(), professor.id(), ModoChamada.CLASSICA, null, "segredo",
                status, abertura, encerramento, null));
    }

    void presenca(Sessao sessao, Usuario aluno) {
        presencas.insert(new Presenca(null, sessao.id(), turma.id(), aluno.id(), true, OrigemPresenca.MANUAL,
                null, null, null, INICIO));
    }
}
