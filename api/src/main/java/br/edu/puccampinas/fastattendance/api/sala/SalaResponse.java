/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.sala;

import br.edu.puccampinas.fastattendance.api.dominio.Sala;

public record SalaResponse(String id, String nome, double latitude, double longitude, double raioMetros) {

    public static SalaResponse de(Sala sala) {
        return new SalaResponse(sala.id(), sala.nome(), sala.latitude(), sala.longitude(), sala.raioMetros());
    }
}
