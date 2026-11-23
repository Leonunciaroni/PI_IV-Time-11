/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.api.repositorio;

import br.edu.puccampinas.fastattendance.api.dominio.Turma;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TurmaRepository extends MongoRepository<Turma, String> {

    List<Turma> findByProfessorId(String professorId);

    /** Busca restrita ao dono: a turma de outro professor é tratada como inexistente. */
    Optional<Turma> findByIdAndProfessorId(String id, String professorId);
}
