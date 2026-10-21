package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.MongoTestcontainers;
import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestcontainers.class)
class TurmaControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    TurmaRepository turmas;

    @Autowired
    MatriculaRepository matriculas;

    @Autowired
    JwtService jwt;

    Usuario professor;
    Usuario outroProfessor;
    Usuario aluno;

    @BeforeEach
    void prepararDados() {
        matriculas.deleteAll();
        turmas.deleteAll();
        usuarios.deleteAll();
        professor = usuario("Prof. Matheus", "matheus@puc.br", Papel.PROFESSOR, "P0001");
        outroProfessor = usuario("Profa. Clara", "clara@puc.br", Papel.PROFESSOR, "P0002");
        aluno = usuario("Ana Souza", "ana@puc.br", Papel.ALUNO, "24001234");
    }

    // ---- Criar e listar ----

    @Test
    void criaTurmaParaOProfessorAutenticado() throws Exception {
        criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Turma A"))
                .andExpect(jsonPath("$.disciplina").value("Engenharia de Software I"))
                .andExpect(jsonPath("$.horarioPadrao").value("Seg 19:00"))
                .andExpect(jsonPath("$.totalAlunos").value(0));

        assertEquals(professor.id(), turmas.findAll().getFirst().professorId());
    }

    @Test
    void removeEspacosDasPontasDosCampos() throws Exception {
        criarTurma(professor, "  Turma A ", " Estrutura de Dados", "Qua 21:00 ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Turma A"))
                .andExpect(jsonPath("$.horarioPadrao").value("Qua 21:00"));
    }

    @Test
    void validaCamposDaTurma() throws Exception {
        criarTurma(professor, "", "   ", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.disciplina").exists())
                .andExpect(jsonPath("$.campos.horarioPadrao").exists());
    }

    @Test
    void listaApenasTurmasDoProfessorComTotalDeAlunos() throws Exception {
        String idA = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));
        criarTurma(professor, "Turma B", "Estrutura de Dados", "Qua 21:00");
        criarTurma(outroProfessor, "Turma X", "Redes", "Sex 08:00");
        matriculas.insert(new Matricula(null, idA, aluno.id(), null));

        mvc.perform(get("/api/turmas").header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].disciplina").value("Engenharia de Software I"))
                .andExpect(jsonPath("$[0].totalAlunos").value(1))
                .andExpect(jsonPath("$[1].disciplina").value("Estrutura de Dados"))
                .andExpect(jsonPath("$[1].totalAlunos").value(0));
    }

    @Test
    void listaVaziaQuandoNaoHaTurmas() throws Exception {
        mvc.perform(get("/api/turmas").header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Acesso ----

    @Test
    void exigeTokenValido() throws Exception {
        mvc.perform(get("/api/turmas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/turmas").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alunoNaoAcessaRotasDeTurma() throws Exception {
        mvc.perform(get("/api/turmas").header("Authorization", bearer(aluno)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Seu perfil não tem acesso a este recurso."));
        criarTurma(aluno, "Turma A", "Engenharia de Software I", "Seg 19:00")
                .andExpect(status().isForbidden());
    }

    // ---- Apoio ----

    Usuario usuario(String nome, String email, Papel papel, String matricula) {
        return usuarios.insert(new Usuario(null, nome, email, "hash-nao-usado", papel, matricula, null));
    }

    String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token();
    }

    ResultActions criarTurma(Usuario dono, String nome, String disciplina, String horario) throws Exception {
        String json = """
                {"nome":"%s","disciplina":"%s","horarioPadrao":"%s"}
                """.formatted(nome, disciplina, horario);
        return mvc.perform(post("/api/turmas").header("Authorization", bearer(dono))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String idDe(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
