/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.repositorio;

import br.edu.puccampinas.fastattendance.api.dominio.Sala;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SalaRepository extends MongoRepository<Sala, String> {
}
