package br.edu.puccampinas.fastattendance.api.turma;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Horário padrão de uma turma no formato {@code Dia HH:MM}, como em {@code Seg 19:00}.
 * <p>
 * O dia vai de segunda a domingo, abreviado ({@code Seg}) ou por extenso ({@code Segunda}), sem
 * diferenciar maiúsculas nem acentos. A hora vai de 00 a 23 e o minuto de 00 a 59. O valor é
 * sempre guardado no formato abreviado, para que a mesma turma tenha uma única grafia.
 */
public final class HorarioPadrao {

    private static final Map<String, String> DIAS = Map.ofEntries(
            Map.entry("seg", "Seg"), Map.entry("segunda", "Seg"),
            Map.entry("ter", "Ter"), Map.entry("terca", "Ter"),
            Map.entry("qua", "Qua"), Map.entry("quarta", "Qua"),
            Map.entry("qui", "Qui"), Map.entry("quinta", "Qui"),
            Map.entry("sex", "Sex"), Map.entry("sexta", "Sex"),
            Map.entry("sab", "Sáb"), Map.entry("sabado", "Sáb"),
            Map.entry("dom", "Dom"), Map.entry("domingo", "Dom"));

    private static final Pattern FORMATO = Pattern.compile("^(\\p{L}+)\\s+(\\d{2}):(\\d{2})$");

    private HorarioPadrao() {
    }

    /** Devolve o horário no formato canônico, ou vazio se o texto não seguir o padrão. */
    public static Optional<String> normalizar(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        Matcher encontrado = FORMATO.matcher(texto.strip());
        if (!encontrado.matches()) {
            return Optional.empty();
        }
        String dia = DIAS.get(semAcentos(encontrado.group(1)).toLowerCase(Locale.ROOT));
        int hora = Integer.parseInt(encontrado.group(2));
        int minuto = Integer.parseInt(encontrado.group(3));
        if (dia == null || hora > 23 || minuto > 59) {
            return Optional.empty();
        }
        return Optional.of("%s %02d:%02d".formatted(dia, hora, minuto));
    }

    private static String semAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
