package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;

import java.util.List;
import java.util.Map;

/**
 * Números do dashboard da turma.
 *
 * @param minimo         percentual mínimo de frequência exigido
 * @param abaixoDoMinimo alunos com frequência menor que o mínimo, do pior para o melhor
 * @param usoPorModo     quantidade de aulas dadas em cada modo de chamada
 */
public record EstatisticasResponse(int aulasDadas, int totalAlunos, double frequenciaMedia, int minimo,
                                   List<AulaEstatistica> aulas, List<AlunoEstatistica> abaixoDoMinimo,
                                   Map<ModoChamada, Long> usoPorModo) {
}
