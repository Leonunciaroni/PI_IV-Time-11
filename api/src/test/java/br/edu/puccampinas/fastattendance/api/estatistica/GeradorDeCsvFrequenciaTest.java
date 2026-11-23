/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeradorDeCsvFrequenciaTest {

    static final Instant INICIO = Instant.parse("2026-10-05T22:00:00Z"); // 19:00 em Brasília

    final Usuario ana = aluno("a1", "Ana Souza", "24001234");
    final Usuario bruno = aluno("a2", "Bruno Lima", "24005678");

    @Test
    void consolidadoTemUmaLinhaPorAlunoEmOrdemAlfabetica() {
        List<Sessao> aulas = List.of(aula("s1", 0), aula("s2", 1));
        List<Presenca> presencas = List.of(presenca("s1", ana, true), presenca("s2", ana, true),
                presenca("s1", bruno, true));

        String csv = GeradorDeCsvFrequencia.consolidado(aulas, List.of(bruno, ana), presencas);

        assertEquals("""
                Nome;Matrícula;Presenças;Aulas;Frequência (%);Situação\r
                Ana Souza;24001234;2;2;100,0;Regular\r
                Bruno Lima;24005678;1;2;50,0;Abaixo de 75%\r
                """, csv);
    }

    @Test
    void consolidadoNaoContaPresencaDesmarcadaNemDeOutraAula() {
        List<Presenca> presencas = List.of(presenca("s1", ana, false), presenca("outra-aula", ana, true));

        String csv = GeradorDeCsvFrequencia.consolidado(List.of(aula("s1", 0)), List.of(ana), presencas);

        assertEquals("Ana Souza;24001234;0;1;0,0;Abaixo de 75%", csv.lines().toList().get(1));
    }

    @Test
    void consolidadoSemAulasNaoMarcaNinguemComoEmRisco() {
        String csv = GeradorDeCsvFrequencia.consolidado(List.of(), List.of(ana), List.of());

        assertEquals("Ana Souza;24001234;0;0;0,0;Sem aulas", csv.lines().toList().get(1));
    }

    @Test
    void chamadaListaPresentesEAusentes() {
        Sessao aula = aula("s1", 0);
        List<Presenca> daAula = List.of(presenca("s1", ana, true));

        String csv = GeradorDeCsvFrequencia.chamada(aula, List.of(bruno, ana), daAula);

        assertEquals("""
                Nome;Matrícula;Situação;Origem;Registrado em\r
                Ana Souza;24001234;Presente;MANUAL;05/10/2026 19:00\r
                Bruno Lima;24005678;Ausente;;\r
                """, csv);
    }

    @Test
    void chamadaTrataPresencaDesmarcadaComoAusente() {
        String csv = GeradorDeCsvFrequencia.chamada(aula("s1", 0), List.of(ana), List.of(presenca("s1", ana, false)));

        assertEquals("Ana Souza;24001234;Ausente;;", csv.lines().toList().get(1));
    }

    @Test
    void neutralizaFormulasEscapaSeparadorEAspas() {
        Usuario perigoso = aluno("a3", "=HYPERLINK(\"http://x\")", "+55");
        Usuario composto = aluno("a4", "Souza; Ana \"Nana\"", "1");

        String csv = GeradorDeCsvFrequencia.consolidado(List.of(), List.of(perigoso, composto), List.of());

        List<String> linhas = csv.lines().toList();
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\";'+55;0;0;0,0;Sem aulas", linhas.get(1));
        assertEquals("\"Souza; Ana \"\"Nana\"\"\";1;0;0;0,0;Sem aulas", linhas.get(2));
    }

    private static Usuario aluno(String id, String nome, String matricula) {
        return new Usuario(id, nome, id + "@puc.br", "hash", Papel.ALUNO, matricula, null);
    }

    private static Sessao aula(String id, int diasDepois) {
        Instant abertura = INICIO.plusSeconds(diasDepois * 86400L);
        return new Sessao(id, "t1", "p1", ModoChamada.CLASSICA, null, "segredo", StatusSessao.ENCERRADA,
                abertura, abertura.plusSeconds(3600), null);
    }

    private static Presenca presenca(String sessaoId, Usuario aluno, boolean presente) {
        return new Presenca(null, sessaoId, "t1", aluno.id(), presente, OrigemPresenca.MANUAL, null, null, null,
                INICIO);
    }
}
