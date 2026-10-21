package br.edu.puccampinas.fastattendance.api.repositorio;

import br.edu.puccampinas.fastattendance.api.dominio.Presenca;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PresencaRepository extends MongoRepository<Presenca, String> {

    List<Presenca> findByTurmaId(String turmaId);

    List<Presenca> findByTurmaIdAndAlunoId(String turmaId, String alunoId);
}
