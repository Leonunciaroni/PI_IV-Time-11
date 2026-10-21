package br.edu.puccampinas.fastattendance.api.sala;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Salas físicas usadas no modo QR Code; o acesso por papel é definido em {@code SegurancaConfig}. */
@RestController
@RequestMapping("/api/salas")
public class SalaController {

    private final SalaService salas;

    public SalaController(SalaService salas) {
        this.salas = salas;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalaResponse criar(@Valid @RequestBody SalaRequest requisicao) {
        return salas.criar(requisicao);
    }

    @GetMapping
    public List<SalaResponse> listar() {
        return salas.listar();
    }
}
