/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Preenche automaticamente os campos {@code @CreatedDate}. */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
