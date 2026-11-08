package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.sessao.Configuracao;
import br.edu.puccampinas.fastattendance.sessao.Servidor;
import br.edu.puccampinas.fastattendance.sessao.servidor.AceitadoraDeConexao;
import br.edu.puccampinas.fastattendance.sessao.servidor.Parceiro;
import br.edu.puccampinas.fastattendance.sessao.servidor.Servicos;

import java.time.Duration;
import java.util.ArrayList;

/**
 * Servidor de sessão no modelo, em porta efêmera, com o MongoDB de teste. Monta o mesmo conjunto que
 * {@link Servidor#main(String[])}, sem o console. Cópia do {@code ServidorDeTeste} do {@code sessao}.
 */
final class ServidorDeTeste implements AutoCloseable {

    static final String SEGREDO = "segredo-dos-testes-com-mais-de-32-bytes-0123456789";

    /**
     * URI que não responde: o servidor monta os serviços sem tocar o banco (o driver conecta na primeira
     * operação), então os testes de protocolo, autenticação e conexão rodam sem Docker.
     */
    static final String MONGO_INDISPONIVEL = "mongodb://127.0.0.1:1/sem_banco?serverSelectionTimeoutMS=300&directConnection=true";

    private final ArrayList<Parceiro> usuarios = new ArrayList<>();
    private final Servicos servicos;
    private final AceitadoraDeConexao aceitadora;

    ServidorDeTeste(Duration prazoAutenticacao, Duration timeoutInatividade) throws Exception {
        this(MongoDeTeste.uri(), prazoAutenticacao, timeoutInatividade);
    }

    ServidorDeTeste(String mongoUri, Duration prazoAutenticacao, Duration timeoutInatividade) throws Exception {
        servicos = Servicos.montar(new Configuracao(SEGREDO, mongoUri, prazoAutenticacao,
                timeoutInatividade, Duration.ofHours(4)));
        aceitadora = new AceitadoraDeConexao("0", usuarios, servicos);
        aceitadora.start();
    }

    int porta() {
        return aceitadora.porta();
    }

    /** O efeito do comando {@code desativar}: envia {@code ComunicadoDeDesligamento} e fecha as conexões. */
    void desativar() {
        Servidor.desligar(usuarios);
    }

    /** Queda sem aviso: fecha os sockets das conexões sem enviar comunicado nenhum. */
    void derrubarConexoes() {
        synchronized (usuarios) {
            for (Parceiro usuario : usuarios) {
                try {
                    usuario.adeus();
                } catch (Exception jaFechada) {
                    // a conexão já pode ter sido encerrada pela ponte
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        aceitadora.encerrar();
        Servidor.desligar(usuarios);
        servicos.close();
    }
}
