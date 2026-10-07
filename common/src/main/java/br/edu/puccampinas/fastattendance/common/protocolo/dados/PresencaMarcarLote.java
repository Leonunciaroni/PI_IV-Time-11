package br.edu.puccampinas.fastattendance.common.protocolo.dados;

import java.util.List;

/** Professor → servidor: marca todos com {@code presente} e aplica o oposto às exceções (modo clássico). */
public record PresencaMarcarLote(String sessaoId, boolean presente, List<String> excecoes) {
}
