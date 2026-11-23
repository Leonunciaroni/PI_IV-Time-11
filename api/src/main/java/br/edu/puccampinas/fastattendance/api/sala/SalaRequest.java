/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.sala;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Os números são objetos para que um campo ausente seja recusado, e não lido como zero. */
public record SalaRequest(
        @NotBlank String nome,
        @NotNull @DecimalMin(value = "-90", message = "A latitude deve estar entre -90 e 90.")
        @DecimalMax(value = "90", message = "A latitude deve estar entre -90 e 90.") Double latitude,
        @NotNull @DecimalMin(value = "-180", message = "A longitude deve estar entre -180 e 180.")
        @DecimalMax(value = "180", message = "A longitude deve estar entre -180 e 180.") Double longitude,
        @NotNull @Positive(message = "O raio deve ser maior que zero.") Double raioMetros) {
}
