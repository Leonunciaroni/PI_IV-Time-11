package br.edu.puccampinas.fastattendance.api.sala;

import br.edu.puccampinas.fastattendance.api.MongoTestcontainers;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.repositorio.SalaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
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
class SalaControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    SalaRepository salas;

    @Autowired
    JwtService jwt;

    Usuario professor;
    Usuario outroProfessor;
    Usuario aluno;

    @BeforeEach
    void prepararDados() {
        salas.deleteAll();
        usuarios.deleteAll();
        professor = usuario("Prof. Matheus", "matheus@puc.br", Papel.PROFESSOR, "P0001");
        outroProfessor = usuario("Profa. Clara", "clara@puc.br", Papel.PROFESSOR, "P0002");
        aluno = usuario("Ana Souza", "ana@puc.br", Papel.ALUNO, "24001234");
    }

    @Test
    void criaSalaComCoordenadasERaio() throws Exception {
        criarSala(professor, """
                {"nome":"  H15 - Sala 203 ","latitude":-22.8337,"longitude":-47.0525,"raioMetros":30}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("H15 - Sala 203"))
                .andExpect(jsonPath("$.latitude").value(-22.8337))
                .andExpect(jsonPath("$.longitude").value(-47.0525))
                .andExpect(jsonPath("$.raioMetros").value(30.0));

        assertEquals(1, salas.count());
    }

    @Test
    void aceitaCoordenadasNosLimites() throws Exception {
        criarSala(professor, """
                {"nome":"Polo","latitude":-90,"longitude":180,"raioMetros":0.5}
                """)
                .andExpect(status().isCreated());
    }

    @Test
    void rejeitaCoordenadasForaDosLimitesSemCriarASala() throws Exception {
        criarSala(professor, """
                {"nome":"H15","latitude":90.1,"longitude":-180.1,"raioMetros":30}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.latitude").value("A latitude deve estar entre -90 e 90."))
                .andExpect(jsonPath("$.campos.longitude").value("A longitude deve estar entre -180 e 180."));

        assertEquals(0, salas.count());
    }

    @Test
    void rejeitaRaioZeroOuNegativo() throws Exception {
        for (String raio : new String[]{"0", "-5"}) {
            criarSala(professor, """
                    {"nome":"H15","latitude":-22.8,"longitude":-47.0,"raioMetros":%s}
                    """.formatted(raio))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.raioMetros").value("O raio deve ser maior que zero."));
        }
        assertEquals(0, salas.count());
    }

    @Test
    void exigeTodosOsCampos() throws Exception {
        criarSala(professor, """
                {"nome":"   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.latitude").exists())
                .andExpect(jsonPath("$.campos.longitude").exists())
                .andExpect(jsonPath("$.campos.raioMetros").exists());
    }

    @Test
    void listaTodasAsSalasEmOrdemAlfabetica() throws Exception {
        criarSala(professor, """
                {"nome":"H15 - Sala 203","latitude":-22.8,"longitude":-47.0,"raioMetros":30}
                """);
        criarSala(outroProfessor, """
                {"nome":"a01 - Auditório","latitude":-22.9,"longitude":-47.1,"raioMetros":50}
                """);

        mvc.perform(get("/api/salas").header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("a01 - Auditório"))
                .andExpect(jsonPath("$[1].nome").value("H15 - Sala 203"));
    }

    @Test
    void listaVaziaQuandoNaoHaSalas() throws Exception {
        mvc.perform(get("/api/salas").header("Authorization", bearer(professor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void exigeTokenDeProfessor() throws Exception {
        mvc.perform(get("/api/salas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/salas").header("Authorization", bearer(aluno))).andExpect(status().isForbidden());
        criarSala(aluno, """
                {"nome":"H15","latitude":-22.8,"longitude":-47.0,"raioMetros":30}
                """)
                .andExpect(status().isForbidden());

        assertEquals(0, salas.count());
    }

    // ---- Apoio ----

    Usuario usuario(String nome, String email, Papel papel, String matricula) {
        return usuarios.insert(new Usuario(null, nome, email, "hash-nao-usado", papel, matricula, null));
    }

    String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token();
    }

    ResultActions criarSala(Usuario usuario, String json) throws Exception {
        return mvc.perform(post("/api/salas").header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
