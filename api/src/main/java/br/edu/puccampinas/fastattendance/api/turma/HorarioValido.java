/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.api.turma;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** O texto deve seguir o formato de {@link HorarioPadrao}. Vazio é tratado por {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = HorarioValido.Validador.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface HorarioValido {

    String message() default "Use o formato Dia HH:MM, como Seg 19:00 (de segunda a domingo, hora de 00 a 23).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<HorarioValido, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.isBlank() || HorarioPadrao.normalizar(valor).isPresent();
        }
    }
}
