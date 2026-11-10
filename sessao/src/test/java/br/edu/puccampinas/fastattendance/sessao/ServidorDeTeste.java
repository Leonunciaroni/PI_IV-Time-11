package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.sessao.servidor.AceitadoraDeConexao;
import br.edu.puccampinas.fastattendance.sessao.servidor.Parceiro;
import br.edu.puccampinas.fastattendance.sessao.servidor.Servicos;

import java.time.Duration;
import java.util.ArrayList;

/**
 * Servidor de sessão em porta efêmera, com o MongoDB de teste. Monta o mesmo conjunto que
 * {@link Servidor#main(String[])}, sem o console.
 */
final class ServidorDeTeste implements AutoCloseable {

    static final String SEGREDO = "segredo-dos-testes-com-mais-de-32-bytes-0123456789";

    private final ArrayList<Parceiro> usuarios = new ArrayList<>();
    private final Servicos servicos;
    private final AceitadoraDeConexao aceitadora;

    ServidorDeTeste(Duration prazoAutenticacao, Duration timeoutInatividade) throws Exception {
        this(prazoAutenticacao, timeoutInatividade, Duration.ofHours(4));
    }

    ServidorDeTeste(Duration prazoAutenticacao, Duration timeoutInatividade, Duration duracaoMaxima)
            throws Exception {
        servicos = Servicos.montar(new Configuracao(SEGREDO, MongoDeTeste.uri(), prazoAutenticacao,
                timeoutInatividade, duracaoMaxima));
        aceitadora = new AceitadoraDeConexao("0", usuarios, servicos);
        aceitadora.start();
    }

    int porta() {
        return aceitadora.porta();
    }

    /** O mesmo efeito do comando {@code desativar}, sem sair da JVM. */
    void desativar() {
        Servidor.desligar(usuarios);
    }

    @Override
    public void close() throws Exception {
        aceitadora.encerrar();
        Servidor.desligar(usuarios);
        servicos.close();
    }
}
