package br.edu.puccampinas.fastattendance.api.repositorio;

import br.edu.puccampinas.fastattendance.api.dominio.Sessao;
import br.edu.puccampinas.fastattendance.common.modelo.StatusSessao;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SessaoRepository extends MongoRepository<Sessao, String> {

    List<Sessao> findByTurmaId(String turmaId);

    List<Sessao> findByTurmaIdInAndStatus(List<String> turmaIds, StatusSessao status);

    List<Sessao> findByTurmaIdAndStatus(String turmaId, StatusSessao status);
}
