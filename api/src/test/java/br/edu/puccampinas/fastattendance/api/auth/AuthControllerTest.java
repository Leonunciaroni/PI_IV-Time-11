/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.auth;

import br.edu.puccampinas.fastattendance.api.MongoTestcontainers;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestcontainers.class)
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    JwtService jwt;

    @BeforeEach
    void limpar() {
        usuarios.deleteAll();
    }

    @Test
    void registraUsuarioSemExporNemPersistirSenhaEmTexto() throws Exception {
        registrar("Prof. Matheus", "Matheus@PUC.br", "senha-segura", "PROFESSOR")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("matheus@puc.br"))
                .andExpect(jsonPath("$.papel").value("PROFESSOR"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        String hash = usuarios.findByEmail("matheus@puc.br").orElseThrow().senhaHash();
        assertTrue(hash.startsWith("$2"), "senha deve ser armazenada como hash BCrypt");
    }

    @Test
    void rejeitaEmailJaCadastradoIgnorandoMaiusculas() throws Exception {
        registrar("Ana", "ana@puc.br", "senha-segura", "ALUNO").andExpect(status().isCreated());

        registrar("Ana 2", "ANA@puc.br", "outra-senha", "ALUNO").andExpect(status().isConflict());
    }

    @Test
    void validaCamposDoCadastro() throws Exception {
        registrar("", "nao-e-email", "curta", "ALUNO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    void recusaProfessorComEmailForaDaListaSemCriarAConta() throws Exception {
        registrar("Intruso", "intruso@gmail.com", "senha-segura", "PROFESSOR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Este e-mail não está autorizado a criar conta de professor."));
        // Domínio parecido, mas não igual ao autorizado (@professores.puc.br)
        registrar("Intruso", "x@falsoprofessores.puc.br", "senha-segura", "PROFESSOR")
                .andExpect(status().isForbidden());

        assertEquals(0, usuarios.count());
    }

    @Test
    void aceitaProfessorDeDominioAutorizado() throws Exception {
        registrar("Profa. Clara", "Clara@Professores.PUC.br", "senha-segura", "PROFESSOR")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("PROFESSOR"));
    }

    @Test
    void alunoSeCadastraComQualquerEmail() throws Exception {
        registrar("Ana", "ana@gmail.com", "senha-segura", "ALUNO").andExpect(status().isCreated());
    }

    @Test
    void recusaDeProfessorNaoRevelaSeOEmailJaExiste() throws Exception {
        registrar("Ana", "ana@gmail.com", "senha-segura", "ALUNO").andExpect(status().isCreated());

        registrar("Ana", "ana@gmail.com", "senha-segura", "PROFESSOR").andExpect(status().isForbidden());
    }

    @Test
    void loginDevolveJwtValidoComPapelDoUsuario() throws Exception {
        registrar("Ana Souza", "ana@puc.br", "senha-segura", "ALUNO");

        String corpo = login("ana@puc.br", "senha-segura")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.nome").value("Ana Souza"))
                .andReturn().getResponse().getContentAsString();

        UsuarioAutenticado usuario = jwt.validar(JsonPath.read(corpo, "$.token"));
        assertEquals(Papel.ALUNO, usuario.papel());
        assertEquals("Ana Souza", usuario.nome());
    }

    @Test
    void loginComSenhaErradaOuEmailInexistenteTemMesmaResposta() throws Exception {
        registrar("Ana", "ana@puc.br", "senha-segura", "ALUNO");

        login("ana@puc.br", "senha-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
        login("ninguem@puc.br", "senha-segura")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
    }

    @Test
    void meExigeTokenValido() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meDevolveDadosDoDonoDoToken() throws Exception {
        registrar("Prof. Matheus", "matheus@puc.br", "senha-segura", "PROFESSOR");
        String corpo = login("matheus@puc.br", "senha-segura").andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.token");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("matheus@puc.br"))
                .andExpect(jsonPath("$.id").exists());
    }

    private ResultActions registrar(String nome, String email, String senha, String papel) throws Exception {
        String json = """
                {"nome":"%s","email":"%s","senha":"%s","papel":"%s","matricula":"24001234"}
                """.formatted(nome, email, senha, papel);
        return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String senha) throws Exception {
        String json = """
                {"email":"%s","senha":"%s"}
                """.formatted(email, senha);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
