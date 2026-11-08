package br.edu.puccampinas.fastattendance.api.turma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HorarioPadraoTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Seg 19:00      | Seg 19:00",
            "Qua 21:30      | Qua 21:30",
            "Dom 00:00      | Dom 00:00",
            "Sex 23:59      | Sex 23:59",
            "segunda 19:00  | Seg 19:00",
            "TERÇA 08:15    | Ter 08:15",
            "terca 08:15    | Ter 08:15",
            "quarta 07:00   | Qua 07:00",
            "quinta 07:00   | Qui 07:00",
            "SEXTA 07:00    | Sex 07:00",
            "Sáb 10:00      | Sáb 10:00",
            "sab 10:00      | Sáb 10:00",
            "sábado 10:00   | Sáb 10:00",
            "Domingo 10:00  | Dom 10:00",
            "'  Seg   19:00 ' | Seg 19:00"})
    void aceitaDiaAbreviadoOuPorExtensoNormalizandoParaOFormatoCurto(String entrada, String esperado) {
        assertEquals(Optional.of(esperado), HorarioPadrao.normalizar(entrada));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ", "Seg", "19:00", "Seg 19", "Seg 9:00", "Seg 19:0", "Seg 19h00", "Seg19:00",
            "Seg 24:00", "Seg 25:00", "Seg 19:60", "Xyz 19:00", "Segundona 19:00", "Seg 19:00 noite",
            "Seg,Qua 19:00", "Seg 19:00:00", "Seg -1:00", "1 19:00", "à noite"})
    void rejeitaTextoForaDoPadrao(String entrada) {
        assertEquals(Optional.empty(), HorarioPadrao.normalizar(entrada));
    }

    @Test
    void aceitaTodosOsDiasDaSemana() {
        for (String dia : new String[]{"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"}) {
            assertEquals(Optional.of(dia + " 12:00"), HorarioPadrao.normalizar(dia + " 12:00"));
        }
    }
}
