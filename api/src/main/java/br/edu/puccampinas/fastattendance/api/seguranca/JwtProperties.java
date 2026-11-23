/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do JWT. O mesmo segredo deve ser usado pelo servidor de sessão.
 *
 * @param segredo       variável de ambiente {@code JWT_SECRET}, com pelo menos 32 bytes
 * @param validadeHoras variável de ambiente {@code JWT_EXPIRACAO_HORAS}
 */
@ConfigurationProperties("fastattendance.jwt")
public record JwtProperties(String segredo, @DefaultValue("12") int validadeHoras) {
}
