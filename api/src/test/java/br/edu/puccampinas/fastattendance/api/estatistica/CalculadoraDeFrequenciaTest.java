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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraDeFrequenciaTest {

    static final Instant INICIO = Instant.parse("2026-10-05T22:00:00Z");

    @Test
    void turmaSemAulasEAlunosDevolveZerosSemDividirPorZero() {
        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(List.of(), List.of(), List.of());

        assertEquals(0, resultado.aulasDadas());
        assertEquals(0, resultado.totalAlunos());
        assertEquals(0.0, resultado.frequenciaMedia());
        assertTrue(resultado.aulas().isEmpty());
        assertTrue(resultado.alunos().isEmpty());
        assertTrue(resultado.abaixoDoMinimo().isEmpty());
    }

    @Test
    void turmaComAlunosMasSemAulasNaoMarcaNinguemAbaixoDoMinimo() {
        List<Usuario> alunos = List.of(aluno("a1", "Ana"), aluno("a2", "Bruno"));

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(List.of(), alunos, List.of());

        assertEquals(2, resultado.totalAlunos());
        assertTrue(resultado.abaixoDoMinimo().isEmpty());
    }

    @Test
    void calculaPercentualDeCadaAulaEAMediaDaTurma() {
        List<Usuario> alunos = List.of(aluno("a1", "Ana"), aluno("a2", "Bruno"), aluno("a3", "Carla"),
                aluno("a4", "Diego"));
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA), aula("s2", 1, ModoChamada.QRCODE));
        List<Presenca> presencas = new ArrayList<>();
        // Aula 1: 4 de 4 (100%). Aula 2: 2 de 4 (50%).
        alunos.forEach(a -> presencas.add(presenca("s1", a.id(), true)));
        presencas.add(presenca("s2", "a1", true));
        presencas.add(presenca("s2", "a2", true));

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(aulas, alunos, presencas);

        assertEquals(2, resultado.aulasDadas());
        assertEquals(100.0, resultado.aulas().get(0).percentual());
        assertEquals(50.0, resultado.aulas().get(1).percentual());
        assertEquals(2, resultado.aulas().get(1).presentes());
        assertEquals(4, resultado.aulas().get(1).matriculados());
        assertEquals(75.0, resultado.frequenciaMedia());
    }

    @Test
    void listaAsAulasEmOrdemCronologica() {
        List<Sessao> aulas = List.of(aula("tarde", 5, ModoChamada.SOM), aula("cedo", 0, ModoChamada.CLASSICA));

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(aulas, List.of(aluno("a1", "Ana")),
                List.of());

        assertEquals("cedo", resultado.aulas().get(0).sessaoId());
        assertEquals("tarde", resultado.aulas().get(1).sessaoId());
    }

    @Test
    void alunoComExatamente75PorCentoNaoFicaAbaixoDoMinimo() {
        // 3 de 4 aulas = 75%
        EstatisticasResponse resultado = calcularComPresencas(4, 3);

        assertTrue(resultado.abaixoDoMinimo().isEmpty());
    }

    @Test
    void alunoComMenosDe75PorCentoFicaAbaixoDoMinimo() {
        // 2 de 3 aulas = 66,7%
        EstatisticasResponse resultado = calcularComPresencas(3, 2);

        assertEquals(1, resultado.abaixoDoMinimo().size());
        AlunoEstatistica aluno = resultado.abaixoDoMinimo().get(0);
        assertEquals("a1", aluno.alunoId());
        assertEquals(2, aluno.presencas());
        assertEquals(3, aluno.aulas());
        assertEquals(66.7, aluno.percentual());
        assertEquals(75, resultado.minimo());
    }

    @Test
    void alunoSemNenhumaPresencaTemZeroPorCento() {
        EstatisticasResponse resultado = calcularComPresencas(3, 0);

        assertEquals(0.0, resultado.abaixoDoMinimo().get(0).percentual());
    }

    @Test
    void ordenaOsAlunosAbaixoDoMinimoDoPiorParaOMelhor() {
        List<Usuario> alunos = List.of(aluno("a1", "Ana"), aluno("a2", "Bruno"), aluno("a3", "Carla"));
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA), aula("s2", 1, ModoChamada.CLASSICA),
                aula("s3", 2, ModoChamada.CLASSICA), aula("s4", 3, ModoChamada.CLASSICA));
        List<Presenca> presencas = List.of(
                presenca("s1", "a1", true), presenca("s2", "a1", true),                  // Ana: 50%
                presenca("s1", "a2", true),                                              // Bruno: 25%
                presenca("s1", "a3", true), presenca("s2", "a3", true),
                presenca("s3", "a3", true), presenca("s4", "a3", true));                 // Carla: 100%

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(aulas, alunos, presencas);

        assertEquals(List.of("Bruno", "Ana"),
                resultado.abaixoDoMinimo().stream().map(AlunoEstatistica::nome).toList());
    }

    @Test
    void listaTodosOsAlunosComQualquerPercentualEmOrdemAlfabetica() {
        List<Usuario> alunos = List.of(aluno("a3", "carla"), aluno("a1", "Bruno"), aluno("a2", "Ana"));
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA), aula("s2", 1, ModoChamada.QRCODE));
        List<Presenca> presencas = List.of(
                presenca("s1", "a2", true), presenca("s2", "a2", true),                  // Ana: 100%
                presenca("s1", "a1", true),                                              // Bruno: 50%
                presenca("s1", "a3", false));                                            // Carla: 0%

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(aulas, alunos, presencas);

        assertEquals(List.of("Ana", "Bruno", "carla"),
                resultado.alunos().stream().map(AlunoEstatistica::nome).toList());
        assertEquals(List.of(100.0, 50.0, 0.0),
                resultado.alunos().stream().map(AlunoEstatistica::percentual).toList());
        assertEquals(new AlunoEstatistica("a1", "Bruno", "RAa1", 1, 2, 50.0), resultado.alunos().get(1));
        // Abaixo do mínimo continua com o mesmo filtro e a mesma ordem
        assertEquals(List.of("carla", "Bruno"),
                resultado.abaixoDoMinimo().stream().map(AlunoEstatistica::nome).toList());
    }

    @Test
    void semAulasEncerradasOsAlunosAparecemComZeroAulas() {
        List<Usuario> alunos = List.of(aluno("a1", "Ana"));

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(List.of(), alunos, List.of());

        assertEquals(List.of(new AlunoEstatistica("a1", "Ana", "RAa1", 0, 0, 0.0)), resultado.alunos());
    }

    @Test
    void presencaDesmarcadaNaoContaComoPresente() {
        List<Usuario> alunos = List.of(aluno("a1", "Ana"));
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA));

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(aulas, alunos,
                List.of(presenca("s1", "a1", false)));

        assertEquals(0, resultado.aulas().get(0).presentes());
        assertEquals(1, resultado.abaixoDoMinimo().size());
    }

    @Test
    void ignoraPresencaDeQuemNaoEstaMatriculadoOuDeOutraAula() {
        List<Usuario> alunos = List.of(aluno("a1", "Ana"));
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA));
        List<Presenca> presencas = List.of(
                presenca("s1", "a1", true),
                presenca("s1", "desconhecido", true),
                presenca("outra-aula", "a1", true));

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(aulas, alunos, presencas);

        assertEquals(1, resultado.aulas().get(0).presentes());
        assertEquals(100.0, resultado.aulas().get(0).percentual());
    }

    @Test
    void contaAulasPorModoIncluindoOsModosNaoUsados() {
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA), aula("s2", 1, ModoChamada.QRCODE),
                aula("s3", 2, ModoChamada.QRCODE));

        EstatisticasResponse resultado = CalculadoraDeFrequencia.calcular(aulas, List.of(), List.of());

        assertEquals(1L, resultado.usoPorModo().get(ModoChamada.CLASSICA));
        assertEquals(2L, resultado.usoPorModo().get(ModoChamada.QRCODE));
        assertEquals(0L, resultado.usoPorModo().get(ModoChamada.SOM));
    }

    /** Um aluno que esteve presente nas primeiras {@code presente} de {@code total} aulas. */
    private static EstatisticasResponse calcularComPresencas(int total, int presente) {
        List<Sessao> aulas = new ArrayList<>();
        List<Presenca> presencas = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            aulas.add(aula("s" + i, i, ModoChamada.CLASSICA));
            if (i < presente) {
                presencas.add(presenca("s" + i, "a1", true));
            }
        }
        return CalculadoraDeFrequencia.calcular(aulas, List.of(aluno("a1", "Ana")), presencas);
    }

    private static Usuario aluno(String id, String nome) {
        return new Usuario(id, nome, nome.toLowerCase() + "@puc.br", "hash", Papel.ALUNO, "RA" + id, null);
    }

    private static Sessao aula(String id, int diasDepois, ModoChamada modo) {
        return new Sessao(id, "turma1", "prof1", modo, null, "segredo", StatusSessao.ENCERRADA,
                INICIO.plusSeconds(diasDepois * 86400L), INICIO.plusSeconds(diasDepois * 86400L + 3600), null);
    }

    private static Presenca presenca(String sessaoId, String alunoId, boolean presente) {
        return new Presenca(null, sessaoId, "turma1", alunoId, presente, OrigemPresenca.MANUAL, null, null, null,
                INICIO);
    }
}
