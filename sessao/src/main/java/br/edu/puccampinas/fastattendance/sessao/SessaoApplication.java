package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeChamadaClassica;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeSessoes;
import br.edu.puccampinas.fastattendance.sessao.protocolo.Json;
import br.edu.puccampinas.fastattendance.sessao.protocolo.RoteadorDeMensagens;
import br.edu.puccampinas.fastattendance.sessao.servidor.ServidorSessao;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ponto de entrada do servidor de sessão em tempo real.
 * <p>
 * Este módulo não usa framework: o caminho crítico de check-in precisa de latência baixa e
 * previsível.
 */
public final class SessaoApplication {

    static {
        if (System.getProperty("java.util.logging.SimpleFormatter.format") == null) {
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "%1$tFT%1$tT.%1$tL %4$-7s [%3$s] %5$s%6$s%n");
        }
    }

    private static final System.Logger LOG = System.getLogger(SessaoApplication.class.getName());

    private SessaoApplication() {
    }

    public static void main(String[] args) throws Exception {
        Configuracao configuracao = Configuracao.doAmbiente(System.getenv());
        ServidorSessao servidor = iniciar(configuracao);

        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            LOG.log(System.Logger.Level.INFO, "Encerrando servidor de sessão");
            try {
                servidor.encerrar(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    /** Monta e inicia o servidor; usado também pelos testes. */
    public static ServidorSessao iniciar(Configuracao configuracao) throws Exception {
        // O servidor de sessão só valida tokens; a validade de emissão não é usada aqui
        JwtService jwt = new JwtService(configuracao.jwtSegredo(), Duration.ofHours(1));
        ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("sessao-agendador").factory());
        Json json = new Json();
        Clock relogio = Clock.systemUTC();

        ConnectionString uri = new ConnectionString(configuracao.mongoUri());
        MongoClient mongo = MongoClients.create(uri);
        MongoDatabase banco = mongo.getDatabase(uri.getDatabase());
        ServicoDeSessoes sessoes = new ServicoDeSessoes(banco, relogio, configuracao.duracaoMaxima());
        ServicoDeChamadaClassica chamadaClassica = new ServicoDeChamadaClassica(banco, sessoes, relogio);
        RoteadorDeMensagens roteador = new RoteadorDeMensagens(json, jwt, agendador, relogio, sessoes,
                chamadaClassica);

        ServidorSessao servidor = new ServidorSessao(configuracao, roteador, agendador, json);
        servidor.aoEncerrar(mongo::close);
        agendarEncerramentoPorTempoLimite(agendador, sessoes, configuracao.duracaoMaxima());
        servidor.start();
        servidor.aguardarInicio(10, TimeUnit.SECONDS);
        return servidor;
    }

    /**
     * Varre periodicamente as sessões vencidas. Toda réplica faz a varredura; como a atualização só
     * altera sessões ainda abertas, cada sessão é encerrada uma única vez.
     */
    private static void agendarEncerramentoPorTempoLimite(ScheduledExecutorService agendador,
                                                          ServicoDeSessoes sessoes, Duration duracaoMaxima) {
        long intervalo = Math.clamp(duracaoMaxima.toMillis() / 4, 100, 30_000);
        agendador.scheduleWithFixedDelay(
                // Em virtual thread: com o MongoDB lento, a consulta não atrasa os prazos de autenticação
                () -> Thread.ofVirtual().name("sessao-tempo-limite").start(() -> {
                    try {
                        long encerradas = sessoes.encerrarVencidas();
                        if (encerradas > 0) {
                            LOG.log(System.Logger.Level.INFO, "Sessões encerradas por tempo limite: {0}", encerradas);
                        }
                    } catch (RuntimeException e) {
                        LOG.log(System.Logger.Level.WARNING, "Falha ao encerrar sessões vencidas", e);
                    }
                }),
                intervalo, intervalo, TimeUnit.MILLISECONDS);
    }
}
