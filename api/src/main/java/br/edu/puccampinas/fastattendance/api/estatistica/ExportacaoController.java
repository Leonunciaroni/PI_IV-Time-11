package br.edu.puccampinas.fastattendance.api.estatistica;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** Download da frequência em CSV; o acesso só para professor é definido em {@code SegurancaConfig}. */
@RestController
@RequestMapping("/api/turmas/{id}/exportacao")
public class ExportacaoController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final String BOM = "﻿";

    private final ExportacaoService exportacao;

    public ExportacaoController(ExportacaoService exportacao) {
        this.exportacao = exportacao;
    }

    @GetMapping("/consolidado")
    public ResponseEntity<byte[]> consolidado(@AuthenticationPrincipal UsuarioAutenticado professor,
                                              @PathVariable String id) {
        return baixar(exportacao.consolidado(professor.id(), id));
    }

    @GetMapping("/aulas/{sessaoId}")
    public ResponseEntity<byte[]> chamada(@AuthenticationPrincipal UsuarioAutenticado professor,
                                          @PathVariable String id, @PathVariable String sessaoId) {
        return baixar(exportacao.chamada(professor.id(), id, sessaoId));
    }

    /** O BOM no começo faz o Excel reconhecer o UTF-8 e mostrar os acentos corretamente. */
    private static ResponseEntity<byte[]> baixar(ExportacaoService.ArquivoCsv arquivo) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .body((BOM + arquivo.conteudo()).getBytes(StandardCharsets.UTF_8));
    }
}
