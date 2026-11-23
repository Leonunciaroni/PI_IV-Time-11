/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Monta o histórico de um aluno com as mesmas regras da frequência da turma. */
public final class CalculadoraDeHistorico {

    private CalculadoraDeHistorico() {
    }

    public static HistoricoResponse calcular(String turmaId, Usuario aluno, List<Sessao> aulas,
                                             List<Presenca> presencasDoAluno) {
        List<Sessao> ordenadas = aulas.stream().sorted(Comparator.comparing(Sessao::abertaEm)).toList();

        // Só conta presente = true; sem registro ou desmarcado é ausência
        Set<String> aulasComPresenca = presencasDoAluno.stream()
                .filter(Presenca::presente)
                .map(Presenca::sessaoId)
                .collect(Collectors.toSet());

        List<AulaDoAluno> historico = ordenadas.stream()
                .map(aula -> new AulaDoAluno(aula.id(), aula.abertaEm(), aula.modo(),
                        aulasComPresenca.contains(aula.id())))
                .toList();

        long presencas = historico.stream().filter(AulaDoAluno::presente).count();
        double percentual = CalculadoraDeFrequencia.percentual(presencas, historico.size());
        boolean abaixoDoMinimo = !historico.isEmpty() && percentual < CalculadoraDeFrequencia.MINIMO;

        return new HistoricoResponse(turmaId, aluno.id(), aluno.nome(), aluno.matricula(), historico.size(),
                presencas, percentual, CalculadoraDeFrequencia.MINIMO, abaixoDoMinimo, historico);
    }
}
