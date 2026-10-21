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

import java.util.Arrays;
import java.util.stream.Collectors;

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

    // ---- Detalhes e alunos ----

    @Test
    void detalhaTurmaComTotalDeAlunos() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));
        matriculas.insert(new Matricula(null, id, aluno.id(), null));

        mvc.perform(get("/api/turmas/" + id).header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nome").value("Turma A"))
                .andExpect(jsonPath("$.totalAlunos").value(1));
    }

    @Test
    void turmaDeOutroProfessorOuInexistenteResponde404() throws Exception {
        String idDeOutro = idDe(criarTurma(outroProfessor, "Turma X", "Redes", "Sex 08:00"));

        mvc.perform(get("/api/turmas/" + idDeOutro).header("Authorization", bearer(professor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Turma não encontrada"));
        mvc.perform(get("/api/turmas/" + idDeOutro + "/alunos").header("Authorization", bearer(professor)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/turmas/nao-existe").header("Authorization", bearer(professor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listaAlunosMatriculadosEmOrdemAlfabeticaSemExporEmail() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));
        Usuario bruno = usuario("bruno Lima", "bruno@puc.br", Papel.ALUNO, "24001777");
        Usuario fora = usuario("Carla Dias", "carla@puc.br", Papel.ALUNO, "24002001");
        matriculas.insert(new Matricula(null, id, bruno.id(), null));
        matriculas.insert(new Matricula(null, id, aluno.id(), null));

        mvc.perform(get("/api/turmas/" + id + "/alunos").header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$[0].matricula").value("24001234"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[1].nome").value("bruno Lima"))
                .andExpect(jsonPath("$[?(@.id == '" + fora.id() + "')]").isEmpty());
    }

    @Test
    void turmaSemAlunosTemListaVazia() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));

        mvc.perform(get("/api/turmas/" + id + "/alunos").header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Candidatos e matrícula ----

    @Test
    void candidatosSaoAlunosForaDaTurmaFiltradosPorNomeOuMatricula() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));
        Usuario carla = usuario("Carla Dias", "carla@puc.br", Papel.ALUNO, "24002001");
        Usuario diego = usuario("Diego Alves", "diego@puc.br", Papel.ALUNO, "24002002");
        matriculas.insert(new Matricula(null, id, aluno.id(), null));

        // Sem busca: todos os alunos de fora da turma; professores nunca aparecem
        mvc.perform(get("/api/turmas/" + id + "/candidatos").header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Carla Dias"))
                .andExpect(jsonPath("$[1].nome").value("Diego Alves"));

        mvc.perform(get("/api/turmas/" + id + "/candidatos").param("busca", "DIEGO")
                        .header("Authorization", bearer(professor)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(diego.id()));

        mvc.perform(get("/api/turmas/" + id + "/candidatos").param("busca", "2001")
                        .header("Authorization", bearer(professor)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(carla.id()));
    }

    @Test
    void buscaTrataOTextoComoLiteralENaoComoExpressaoRegular() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));

        mvc.perform(get("/api/turmas/" + id + "/candidatos").param("busca", ".*")
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/turmas/" + id + "/candidatos").param("busca", "(")
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void matriculaAlunosEIgnoraOsQueJaEstaoNaTurma() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));
        Usuario carla = usuario("Carla Dias", "carla@puc.br", Papel.ALUNO, "24002001");
        matriculas.insert(new Matricula(null, id, aluno.id(), null));

        matricular(professor, id, aluno.id(), carla.id(), carla.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matriculados").value(1))
                .andExpect(jsonPath("$.jaMatriculados").value(1))
                .andExpect(jsonPath("$.totalAlunos").value(2));

        assertEquals(2, matriculas.countByTurmaId(id));
    }

    @Test
    void rejeitaMatriculaDeIdInexistenteOuQueNaoSejaAluno() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));

        matricular(professor, id, aluno.id(), "nao-existe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A lista tem alunos que não existem."));
        matricular(professor, id, outroProfessor.id())
                .andExpect(status().isBadRequest());

        // Nada é gravado quando a lista tem algum id inválido
        assertEquals(0, matriculas.countByTurmaId(id));
    }

    @Test
    void validaListaDeAlunosDaMatricula() throws Exception {
        String id = idDe(criarTurma(professor, "Turma A", "Engenharia de Software I", "Seg 19:00"));

        mvc.perform(post("/api/turmas/" + id + "/matriculas").header("Authorization", bearer(professor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"alunoIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.alunoIds").exists());
    }

    @Test
    void naoMatriculaNemBuscaCandidatosEmTurmaDeOutroProfessor() throws Exception {
        String idDeOutro = idDe(criarTurma(outroProfessor, "Turma X", "Redes", "Sex 08:00"));

        matricular(professor, idDeOutro, aluno.id()).andExpect(status().isNotFound());
        mvc.perform(get("/api/turmas/" + idDeOutro + "/candidatos").header("Authorization", bearer(professor)))
                .andExpect(status().isNotFound());
        assertEquals(0, matriculas.countByTurmaId(idDeOutro));
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

    ResultActions matricular(Usuario dono, String turmaId, String... alunoIds) throws Exception {
        String lista = Arrays.stream(alunoIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return mvc.perform(post("/api/turmas/" + turmaId + "/matriculas").header("Authorization", bearer(dono))
                .contentType(MediaType.APPLICATION_JSON).content("{\"alunoIds\":[" + lista + "]}"));
    }

    String idDe(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
