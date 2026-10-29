package br.edu.puccampinas.fastattendance.api.auth;

import br.edu.puccampinas.fastattendance.api.MongoTestcontainers;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestcontainers.class)
class PerfilControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    Usuario aluno;
    Usuario professor;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        aluno = usuario("Ana", "ana@teste.br", Papel.ALUNO);
        professor = usuario("Professor", "prof@teste.br", Papel.PROFESSOR);
    }

    @Test
    void alunoAtualizaDadosSemMudarPapelMatriculaOuSenha() throws Exception {
        atualizar(aluno, """
                {"nome":" Ana Souza ","email":"ANA.NOVA@TESTE.BR","papel":"PROFESSOR","matricula":"999"}
                """)
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana.nova@teste.br"))
                .andExpect(jsonPath("$.papel").value("ALUNO"))
                .andExpect(jsonPath("$.matricula").value("123"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        assertEquals(aluno.senhaHash(), usuarios.findById(aluno.id()).orElseThrow().senhaHash());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(aluno)))
                .andExpect(jsonPath("$.email").value("ana.nova@teste.br"));
    }

    @Test
    void professorAtualizaNomePreservandoEmailEOAluno() throws Exception {
        atualizar(professor, "{\"nome\":\"Prof. Silva\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(professor.email()));
        assertEquals(aluno, usuarios.findById(aluno.id()).orElseThrow());
    }

    @Test
    void emailContinuaUnicoSemDiferenciarMaiusculasEAtualizacaoEAtomica() throws Exception {
        atualizar(aluno, "{\"nome\":\"Não salvar\",\"email\":\"PROF@TESTE.BR\"}")
                .andExpect(status().isConflict());
        assertEquals(aluno, usuarios.findById(aluno.id()).orElseThrow());
        atualizar(aluno, "{\"email\":\"ANA@TESTE.BR\"}").andExpect(status().isOk());
    }

    @Test
    void trocaSenhaSomenteComSenhaAtualCorreta() throws Exception {
        atualizar(aluno, "{\"novaSenha\":\"nova-segura\"}").andExpect(status().isBadRequest());
        atualizar(aluno, "{\"novaSenha\":\"nova-segura\",\"senhaAtual\":\"incorreta\"}")
                .andExpect(status().isBadRequest());
        assertEquals(aluno.senhaHash(), usuarios.findById(aluno.id()).orElseThrow().senhaHash());
        atualizar(aluno, "{\"novaSenha\":\"nova-segura\",\"senhaAtual\":\"senha-original\"}")
                .andExpect(status().isOk());
        var atualizado = usuarios.findById(aluno.id()).orElseThrow();
        assertTrue(encoder.matches("nova-segura", atualizado.senhaHash()));
        assertFalse(encoder.matches("senha-original", atualizado.senhaHash()));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@teste.br\",\"senha\":\"nova-segura\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@teste.br\",\"senha\":\"senha-original\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejeitaCamposInvalidosSemAlterarDados() throws Exception {
        for (String corpo : new String[]{"{}", "{\"nome\":\"   \"}", "{\"email\":\"invalido\"}",
                "{\"email\":\"\"}", "{\"novaSenha\":\"curta\"}", "{\"senhaAtual\":\"senha-original\"}",
                "{\"novaSenha\":\"        \",\"senhaAtual\":\"senha-original\"}"}) {
            atualizar(aluno, corpo).andExpect(status().isBadRequest());
        }
        atualizar(aluno, "{\"novaSenha\":\"" + "á".repeat(40) + "\",\"senhaAtual\":\"senha-original\"}")
                .andExpect(status().isBadRequest());
        assertEquals(aluno, usuarios.findById(aluno.id()).orElseThrow());
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(patch("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\"}"))
                .andExpect(status().isUnauthorized());
    }

    Usuario usuario(String nome, String email, Papel papel) {
        Usuario criado = usuarios.insert(new Usuario(null, nome, email, encoder.encode("senha-original"), papel, "123", null));
        // MongoDB persiste datas com precisão de milissegundos.
        return usuarios.findById(criado.id()).orElseThrow();
    }
    String bearer(Usuario usuario) { return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token(); }
    ResultActions atualizar(Usuario usuario, String corpo) throws Exception {
        return mvc.perform(patch("/api/auth/me").header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
