package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcula a frequência de uma turma. Não acessa o banco: recebe as aulas já encerradas, os alunos
 * matriculados e as presenças, o que facilita testar as regras.
 */
public final class CalculadoraDeFrequencia {

    /** Frequência mínima, em porcentagem, para o aluno não ficar em risco. */
    public static final int MINIMO = 75;

    private CalculadoraDeFrequencia() {
    }

    public static EstatisticasResponse calcular(List<Sessao> aulas, List<Usuario> alunos, List<Presenca> presencas) {
        List<Sessao> ordenadas = aulas.stream().sorted(Comparator.comparing(Sessao::abertaEm)).toList();
        Set<String> idsDasAulas = ordenadas.stream().map(Sessao::id).collect(Collectors.toSet());
        Set<String> idsDosAlunos = alunos.stream().map(Usuario::id).collect(Collectors.toSet());

        // Só vale presente = true, de aluno matriculado e de aula que entrou no cálculo.
        // O professor pode ter desmarcado alguém no modo clássico (presente = false).
        List<Presenca> validas = presencas.stream()
                .filter(Presenca::presente)
                .filter(p -> idsDasAulas.contains(p.sessaoId()))
                .filter(p -> idsDosAlunos.contains(p.alunoId()))
                .toList();

        Map<String, Long> presentesPorAula = validas.stream()
                .collect(Collectors.groupingBy(Presenca::sessaoId, Collectors.counting()));
        Map<String, Long> presencasPorAluno = validas.stream()
                .collect(Collectors.groupingBy(Presenca::alunoId, Collectors.counting()));

        List<AulaEstatistica> estatisticasDasAulas = ordenadas.stream()
                .map(aula -> {
                    long presentes = presentesPorAula.getOrDefault(aula.id(), 0L);
                    return new AulaEstatistica(aula.id(), aula.abertaEm(), aula.modo(), presentes,
                            alunos.size(), percentual(presentes, alunos.size()));
                })
                .toList();

        double frequenciaMedia = arredondar(estatisticasDasAulas.stream()
                .mapToDouble(AulaEstatistica::percentual)
                .average()
                .orElse(0));

        List<AlunoEstatistica> abaixoDoMinimo = alunos.stream()
                .map(aluno -> {
                    long presencasDoAluno = presencasPorAluno.getOrDefault(aluno.id(), 0L);
                    return new AlunoEstatistica(aluno.id(), aluno.nome(), aluno.matricula(), presencasDoAluno,
                            ordenadas.size(), percentual(presencasDoAluno, ordenadas.size()));
                })
                .filter(aluno -> aluno.aulas() > 0 && aluno.percentual() < MINIMO)
                .sorted(Comparator.comparingDouble(AlunoEstatistica::percentual)
                        .thenComparing(AlunoEstatistica::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new EstatisticasResponse(ordenadas.size(), alunos.size(), frequenciaMedia, MINIMO,
                estatisticasDasAulas, abaixoDoMinimo, usoPorModo(ordenadas));
    }

    /** Todos os modos aparecem na resposta, mesmo os que nunca foram usados. */
    private static Map<ModoChamada, Long> usoPorModo(List<Sessao> aulas) {
        Map<ModoChamada, Long> uso = new EnumMap<>(ModoChamada.class);
        for (ModoChamada modo : ModoChamada.values()) {
            uso.put(modo, 0L);
        }
        aulas.forEach(aula -> uso.merge(aula.modo(), 1L, Long::sum));
        return uso;
    }

    static double percentual(long parte, long total) {
        return total == 0 ? 0 : arredondar(parte * 100.0 / total);
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}
