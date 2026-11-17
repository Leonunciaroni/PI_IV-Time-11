package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monta os CSVs de frequência. Não acessa o banco. O formato é o que o Excel em português abre
 * direto: ponto e vírgula como separador, vírgula decimal e acentos preservados (o BOM é
 * acrescentado por quem grava o arquivo).
 */
public final class GeradorDeCsvFrequencia {

    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setRecordSeparator("\r\n")
            .build();

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private GeradorDeCsvFrequencia() {
    }

    /** Uma linha por aluno matriculado, com presenças, aulas dadas e percentual acumulado. */
    public static String consolidado(List<Sessao> aulas, List<Usuario> alunos, List<Presenca> presencas) {
        Set<String> idsDasAulas = aulas.stream().map(Sessao::id).collect(Collectors.toSet());
        Map<String, Long> presencasPorAluno = presencas.stream()
                .filter(Presenca::presente)
                .filter(p -> idsDasAulas.contains(p.sessaoId()))
                .collect(Collectors.groupingBy(Presenca::alunoId, Collectors.counting()));

        return escrever(printer -> {
            printer.printRecord("Nome", "Matrícula", "Presenças", "Aulas", "Frequência (%)", "Situação");
            for (Usuario aluno : porNome(alunos)) {
                long presentes = presencasPorAluno.getOrDefault(aluno.id(), 0L);
                double percentual = CalculadoraDeFrequencia.percentual(presentes, aulas.size());
                String situacao = aulas.isEmpty() ? "Sem aulas"
                        : percentual < CalculadoraDeFrequencia.MINIMO ? "Abaixo de " + CalculadoraDeFrequencia.MINIMO + "%"
                        : "Regular";
                printer.printRecord(texto(aluno.nome()), texto(aluno.matricula()), presentes, aulas.size(),
                        decimal(percentual), situacao);
            }
        });
    }

    /** A chamada de uma aula: todos os alunos matriculados, presentes ou ausentes. */
    public static String chamada(Sessao aula, List<Usuario> alunos, List<Presenca> presencasDaAula) {
        Map<String, Presenca> porAluno = presencasDaAula.stream()
                .collect(Collectors.toMap(Presenca::alunoId, Function.identity(), (a, b) -> a));

        return escrever(printer -> {
            printer.printRecord("Nome", "Matrícula", "Situação", "Origem", "Registrado em");
            for (Usuario aluno : porNome(alunos)) {
                Presenca presenca = porAluno.get(aluno.id());
                boolean presente = presenca != null && presenca.presente();
                printer.printRecord(texto(aluno.nome()), texto(aluno.matricula()),
                        presente ? "Presente" : "Ausente",
                        presente && presenca.origem() != null ? presenca.origem().name() : "",
                        presente && presenca.registradoEm() != null
                                ? DATA_HORA.format(presenca.registradoEm().atZone(FUSO)) : "");
            }
        });
    }

    /** Nome do arquivo de uma aula, com a data de abertura. */
    public static String dataDaAula(Sessao aula) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(aula.abertaEm().atZone(FUSO));
    }

    private interface Escritor {
        void escrever(CSVPrinter printer) throws IOException;
    }

    private static String escrever(Escritor escritor) {
        StringWriter saida = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(saida, FORMATO)) {
            escritor.escrever(printer);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao montar o CSV", e);
        }
        return saida.toString();
    }

    private static List<Usuario> porNome(List<Usuario> alunos) {
        return alunos.stream().sorted(Comparator.comparing(Usuario::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static String decimal(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", valor);
    }

    /**
     * Impede injeção de fórmula: o Excel executa uma célula que começa com =, +, - ou @. O apóstrofo
     * na frente faz o texto ser tratado como texto.
     */
    static String texto(String valor) {
        if (valor == null) {
            return "";
        }
        return !valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
    }
}
