package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Lado cliente de uma conexão com o servidor de sessão, como o {@code Parceiro} do modelo
 * (material "Fazedor de Continhas", pasta {@code Cliente/}).
 * <p>
 * Cópia enxuta do {@code sessao/servidor/Parceiro}: sem o semáforo de leitura antecipada, porque aqui
 * a leitura é feita só pela thread do servidor. A escrita é sincronizada e faz {@code reset()} após cada
 * comunicado, como no servidor, para o fluxo não acumular os objetos da conexão.
 */
final class Parceiro {

    private final Socket conexao;
    private final ObjectInputStream receptor;
    private final ObjectOutputStream transmissor;

    Parceiro(Socket conexao, ObjectInputStream receptor, ObjectOutputStream transmissor) {
        this.conexao = conexao;
        this.receptor = receptor;
        this.transmissor = transmissor;
    }

    synchronized void receba(Comunicado comunicado) throws IOException {
        transmissor.writeObject(comunicado);
        transmissor.flush();
        transmissor.reset();
    }

    Comunicado envie() throws IOException, ClassNotFoundException {
        return (Comunicado) receptor.readObject();
    }

    /** Fecha o socket; idempotente, e falhas aqui não importam (a conexão já está encerrada). */
    void adeus() {
        try {
            conexao.close();
        } catch (IOException falha) {
            // já fechada
        }
    }
}
