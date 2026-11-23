/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.sala;

import br.edu.puccampinas.fastattendance.api.dominio.Sala;
import br.edu.puccampinas.fastattendance.api.repositorio.SalaRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class SalaService {

    private final SalaRepository salas;

    public SalaService(SalaRepository salas) {
        this.salas = salas;
    }

    public SalaResponse criar(SalaRequest requisicao) {
        Sala nova = new Sala(null, requisicao.nome().strip(), requisicao.latitude(), requisicao.longitude(),
                requisicao.raioMetros());
        return SalaResponse.de(salas.insert(nova));
    }

    /** Todas as salas, em ordem alfabética: as salas são do campus, não de um professor. */
    public List<SalaResponse> listar() {
        return salas.findAll().stream()
                .sorted(Comparator.comparing(Sala::nome, String.CASE_INSENSITIVE_ORDER))
                .map(SalaResponse::de)
                .toList();
    }
}
