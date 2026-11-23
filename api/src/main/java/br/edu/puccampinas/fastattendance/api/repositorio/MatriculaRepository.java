/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
package br.edu.puccampinas.fastattendance.api.repositorio;

import br.edu.puccampinas.fastattendance.api.dominio.Matricula;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatriculaRepository extends MongoRepository<Matricula, String> {

    List<Matricula> findByAlunoId(String alunoId);

    List<Matricula> findByTurmaId(String turmaId);

    long countByTurmaId(String turmaId);

    boolean existsByTurmaIdAndAlunoId(String turmaId, String alunoId);

    long deleteByTurmaIdAndAlunoId(String turmaId, String alunoId);
}
