package br.edu.puccampinas.fastattendance.sessao.servidor;

/** Trata uma mensagem de texto recebida em uma conexão. */
@FunctionalInterface
public interface ProcessadorDeMensagens {

    void processar(Conexao conexao, String texto);
}
