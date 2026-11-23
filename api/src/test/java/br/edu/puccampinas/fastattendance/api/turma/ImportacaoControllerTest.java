/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.MongoTestcontainers;
import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.repositorio.MatriculaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.TurmaRepository;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestcontainers.class)
class ImportacaoControllerTest {

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
    Usuario ana;
    Usuario bia;
    Turma turma;

    @BeforeEach
    void prepararDados() {
        matriculas.deleteAll();
        turmas.deleteAll();
        usuarios.deleteAll();
        professor = usuario("Prof. Matheus", "matheus@puc.br", Papel.PROFESSOR, "P0001");
        outroProfessor = usuario("Profa. Clara", "clara@puc.br", Papel.PROFESSOR, "P0002");
        ana = usuario("Ana Souza", "ana@puc.br", Papel.ALUNO, "24001234");
        bia = usuario("Bia Lima", "bia@puc.br", Papel.ALUNO, "24005678");
        turma = turmas.insert(new Turma(null, "Turma A", "Engenharia de Software I", "Seg 19:00",
                professor.id(), null));
    }

    @Test
    void matriculaOsAlunosDoArquivo() throws Exception {
        importar(professor, turma.id(), """
                nome,email,matricula
                Ana Souza,ana@puc.br,24001234
                Bia Lima,BIA@puc.br,24005678
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(2))
                .andExpect(jsonPath("$.jaMatriculados").value(0))
                .andExpect(jsonPath("$.rejeitados").isEmpty())
                .andExpect(jsonPath("$.totalAlunos").value(2));

        assertEquals(2, matriculas.countByTurmaId(turma.id()));
    }

    @Test
    void importarDuasVezesNaoDuplicaMatriculas() throws Exception {
        String csv = "nome,email,matricula\nAna Souza,ana@puc.br,24001234\n";
        importar(professor, turma.id(), csv).andExpect(status().isOk());

        importar(professor, turma.id(), csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(0))
                .andExpect(jsonPath("$.jaMatriculados").value(1))
                .andExpect(jsonPath("$.totalAlunos").value(1));
    }

    @Test
    void alunoJaMatriculadoPorOutroCaminhoNaoEhReimportado() throws Exception {
        matriculas.insert(new Matricula(null, turma.id(), ana.id(), null));

        importar(professor, turma.id(), "nome,email,matricula\nAna Souza,ana@puc.br,24001234\n")
                .andExpect(jsonPath("$.jaMatriculados").value(1))
                .andExpect(jsonPath("$.importados").value(0));
    }

    @Test
    void relataLinhasRejeitadasEImportaAsValidas() throws Exception {
        importar(professor, turma.id(), """
                nome,email,matricula
                Ana Souza,ana@puc.br,24001234
                Fulano,naoexiste@puc.br,99999999
                Bia Lima,bia@puc.br,00000000
                Sem Email,,12345678
                Email Ruim,isso-nao-e-email,12345678
                Ana de novo,ana@puc.br,24001234
                Prof,matheus@puc.br,P0001
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(1))
                .andExpect(jsonPath("$.rejeitados.length()").value(6))
                .andExpect(jsonPath("$.rejeitados[0].linha").value(3))
                .andExpect(jsonPath("$.rejeitados[0].motivo").value("Aluno não cadastrado."))
                .andExpect(jsonPath("$.rejeitados[1].motivo").value("Matrícula não confere com o cadastro."))
                .andExpect(jsonPath("$.rejeitados[2].motivo").value("Nome, e-mail e matrícula são obrigatórios."))
                .andExpect(jsonPath("$.rejeitados[3].motivo").value("E-mail inválido."))
                .andExpect(jsonPath("$.rejeitados[4].motivo").value("E-mail repetido no arquivo."))
                .andExpect(jsonPath("$.rejeitados[5].motivo").value("Aluno não cadastrado."));

        assertEquals(1, matriculas.countByTurmaId(turma.id()));
    }

    @Test
    void arquivoInvalidoRetornaErro() throws Exception {
        importar(professor, turma.id(), "nome,email\nAna,ana@puc.br\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O cabeçalho precisa ter as colunas nome, email e matricula."));
        importar(professor, turma.id(), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Envie um arquivo CSV."));
    }

    @Test
    void csvComAspasForaDoLugarOuArquivoBinarioRetorna400() throws Exception {
        importar(professor, turma.id(), "nome,email,matricula\n\"Ana\" Souza,ana@puc.br,24001234\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(LeitorCsvAlunos.ARQUIVO_ILEGIVEL));

        // Começo de um PNG, com aspas no meio dos bytes
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', '"', 'I', '"', 'H', 0, 0, ',', 'x'};
        mvc.perform(multipart("/api/turmas/" + turma.id() + "/importacao")
                        .file(new MockMultipartFile("arquivo", "foto.png", "image/png", png))
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isBadRequest());

        assertEquals(0, matriculas.countByTurmaId(turma.id()));
    }

    @Test
    void semArquivoRetornaErro() throws Exception {
        mvc.perform(multipart("/api/turmas/" + turma.id() + "/importacao")
                        .header("Authorization", bearer(professor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Envie um arquivo CSV."));
    }

    @Test
    void turmaDeOutroProfessorRespondeComoInexistente() throws Exception {
        importar(outroProfessor, turma.id(), "nome,email,matricula\nAna Souza,ana@puc.br,24001234\n")
                .andExpect(status().isNotFound());

        assertEquals(0, matriculas.countByTurmaId(turma.id()));
    }

    @Test
    void alunoNaoPodeImportar() throws Exception {
        importar(ana, turma.id(), "nome,email,matricula\nBia Lima,bia@puc.br,24005678\n")
                .andExpect(status().isForbidden());
    }

    // ---- Apoio ----

    ResultActions importar(Usuario quem, String turmaId, String conteudo) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "alunos.csv", "text/csv",
                conteudo.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/turmas/" + turmaId + "/importacao").file(arquivo)
                .header("Authorization", bearer(quem)));
    }

    Usuario usuario(String nome, String email, Papel papel, String matricula) {
        return usuarios.insert(new Usuario(null, nome, email, "hash-nao-usado", papel, matricula, null));
    }

    String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.id(), usuario.nome(), usuario.papel()).token();
    }
}
