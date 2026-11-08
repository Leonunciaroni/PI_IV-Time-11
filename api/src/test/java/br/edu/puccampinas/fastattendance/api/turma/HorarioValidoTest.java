package br.edu.puccampinas.fastattendance.api.turma;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorarioValidoTest {

    private static final String MENSAGEM =
            "Use o formato Dia HH:MM, como Seg 19:00 (de segunda a domingo, hora de 00 a 23).";

    private static jakarta.validation.ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void criarValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void fecharFabrica() {
        fabrica.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Seg 19:00", "segunda 19:00", "Sáb 08:30", "Dom 00:00", "Sex 23:59"})
    void aceitaHorarioNoPadrao(String horario) {
        assertTrue(new HorarioValido.Validador().isValid(horario, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"à noite", "Seg 24:00", "Seg 19:60", "Xyz 19:00", "19:00", "Seg 9:00"})
    void rejeitaHorarioForaDoPadrao(String horario) {
        assertFalse(new HorarioValido.Validador().isValid(horario, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deixaTextoVazioParaONotBlank(String horario) {
        assertTrue(new HorarioValido.Validador().isValid(horario, null));
    }

    @Test
    void anotacaoFuncionaNoCampoDoRequest() {
        Set<ConstraintViolation<TurmaRequest>> violacoes =
                validador.validate(new TurmaRequest("Turma A", "Redes", "Seg 24:00"));

        assertEquals(1, violacoes.size());
        ConstraintViolation<TurmaRequest> violacao = violacoes.iterator().next();
        assertEquals("horarioPadrao", violacao.getPropertyPath().toString());
        assertEquals(MENSAGEM, violacao.getMessage());
    }

    @Test
    void requestValidoNaoTemViolacoes() {
        assertTrue(validador.validate(new TurmaRequest("Turma A", "Redes", "Seg 19:00")).isEmpty());
    }

    @Test
    void horarioEmBrancoViolaSoOObrigatorio() {
        Set<ConstraintViolation<TurmaRequest>> violacoes =
                validador.validate(new TurmaRequest("Turma A", "Redes", "  "));

        assertEquals(1, violacoes.size());
        assertFalse(violacoes.iterator().next().getMessage().equals(MENSAGEM),
                "o campo vazio deve acusar só a obrigatoriedade, sem a mensagem de formato");
    }
}
