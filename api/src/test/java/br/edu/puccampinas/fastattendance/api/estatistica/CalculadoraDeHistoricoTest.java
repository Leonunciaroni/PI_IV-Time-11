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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraDeHistoricoTest {

    static final Instant INICIO = Instant.parse("2026-10-05T22:00:00Z");
    static final Usuario ANA = new Usuario("a1", "Ana Souza", "ana@puc.br", "hash", Papel.ALUNO, "24001234", null);

    @Test
    void turmaSemAulasDevolveHistoricoVazioSemDividirPorZero() {
        HistoricoResponse resultado = CalculadoraDeHistorico.calcular("t1", ANA, List.of(), List.of());

        assertEquals(0, resultado.aulasDadas());
        assertEquals(0.0, resultado.percentual());
        assertFalse(resultado.abaixoDoMinimo());
        assertTrue(resultado.aulas().isEmpty());
    }

    @Test
    void copiaOsDadosDoAlunoEDaTurma() {
        HistoricoResponse resultado = CalculadoraDeHistorico.calcular("t1", ANA, List.of(), List.of());

        assertEquals("t1", resultado.turmaId());
        assertEquals("a1", resultado.alunoId());
        assertEquals("Ana Souza", resultado.nome());
        assertEquals("24001234", resultado.matricula());
        assertEquals(75, resultado.minimo());
    }

    @Test
    void indicaPresencaOuAusenciaEmCadaAula() {
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA), aula("s2", 1, ModoChamada.QRCODE),
                aula("s3", 2, ModoChamada.SOM));
        List<Presenca> presencas = List.of(presenca("s1", true), presenca("s3", true));

        HistoricoResponse resultado = CalculadoraDeHistorico.calcular("t1", ANA, aulas, presencas);

        assertEquals(3, resultado.aulasDadas());
        assertEquals(2, resultado.presencas());
        assertEquals(66.7, resultado.percentual());
        assertEquals(List.of(true, false, true), resultado.aulas().stream().map(AulaDoAluno::presente).toList());
        assertEquals(ModoChamada.QRCODE, resultado.aulas().get(1).modo());
    }

    @Test
    void listaAsAulasEmOrdemCronologica() {
        List<Sessao> aulas = List.of(aula("tarde", 5, ModoChamada.SOM), aula("cedo", 0, ModoChamada.CLASSICA));

        HistoricoResponse resultado = CalculadoraDeHistorico.calcular("t1", ANA, aulas, List.of());

        assertEquals("cedo", resultado.aulas().get(0).sessaoId());
        assertEquals("tarde", resultado.aulas().get(1).sessaoId());
    }

    @Test
    void exatamente75PorCentoNaoEstaAbaixoDoMinimo() {
        // 3 de 4 aulas = 75%
        HistoricoResponse resultado = calcularComPresencas(4, 3);

        assertEquals(75.0, resultado.percentual());
        assertFalse(resultado.abaixoDoMinimo());
    }

    @Test
    void menosDe75PorCentoEstaAbaixoDoMinimo() {
        // 2 de 3 aulas = 66,7%
        HistoricoResponse resultado = calcularComPresencas(3, 2);

        assertTrue(resultado.abaixoDoMinimo());
    }

    @Test
    void semNenhumaPresencaTemZeroPorCentoEEstaAbaixoDoMinimo() {
        HistoricoResponse resultado = calcularComPresencas(3, 0);

        assertEquals(0.0, resultado.percentual());
        assertTrue(resultado.abaixoDoMinimo());
    }

    @Test
    void presencaDesmarcadaContaComoAusencia() {
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA));

        HistoricoResponse resultado = CalculadoraDeHistorico.calcular("t1", ANA, aulas,
                List.of(presenca("s1", false)));

        assertFalse(resultado.aulas().get(0).presente());
        assertEquals(0, resultado.presencas());
    }

    @Test
    void ignoraPresencaDeAulaForaDoHistorico() {
        List<Sessao> aulas = List.of(aula("s1", 0, ModoChamada.CLASSICA));

        HistoricoResponse resultado = CalculadoraDeHistorico.calcular("t1", ANA, aulas,
                List.of(presenca("outra-aula", true)));

        assertEquals(0, resultado.presencas());
        assertEquals(0.0, resultado.percentual());
    }

    /** O aluno esteve presente nas primeiras {@code presente} de {@code total} aulas. */
    private static HistoricoResponse calcularComPresencas(int total, int presente) {
        List<Sessao> aulas = new ArrayList<>();
        List<Presenca> presencas = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            aulas.add(aula("s" + i, i, ModoChamada.CLASSICA));
            if (i < presente) {
                presencas.add(presenca("s" + i, true));
            }
        }
        return CalculadoraDeHistorico.calcular("t1", ANA, aulas, presencas);
    }

    private static Sessao aula(String id, int diasDepois, ModoChamada modo) {
        Instant abertura = INICIO.plusSeconds(diasDepois * 86400L);
        return new Sessao(id, "t1", "prof1", modo, null, "segredo", StatusSessao.ENCERRADA,
                abertura, abertura.plusSeconds(3600), null);
    }

    private static Presenca presenca(String sessaoId, boolean presente) {
        return new Presenca(null, sessaoId, "t1", "a1", presente, OrigemPresenca.MANUAL, null, null, null, INICIO);
    }
}
