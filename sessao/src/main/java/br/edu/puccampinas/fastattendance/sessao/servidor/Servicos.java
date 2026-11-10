package br.edu.puccampinas.fastattendance.sessao.servidor;

import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.sessao.Configuracao;
import br.edu.puccampinas.fastattendance.sessao.chamada.ObservadorDeMudancas;
import br.edu.puccampinas.fastattendance.sessao.chamada.PainelAoVivo;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeCheckin;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeChamadaClassica;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeSessoes;
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
 * Serviços usados pelas supervisoras: regras de negócio, JWT, relógio, o MongoDB e o agendador.
 * <p>
 * Montado uma vez por processo; {@link #close()} libera o observador, o agendador e o cliente do
 * MongoDB, nessa ordem, porque o observador usa o cliente.
 */
public final class Servicos implements AutoCloseable
{
    private final Configuracao          configuracao;
    private final JwtService            jwt;
    private final Clock                 relogio;
    private final ServicoDeSessoes      sessoes;
    private final ServicoDeChamadaClassica chamadaClassica;
    private final PainelAoVivo          painel;
    private final ServicoDeCheckin      checkin;
    private final ScheduledExecutorService agendador;
    private final MongoClient           mongo;
    private final ObservadorDeMudancas  observador;

    private Servicos (Configuracao configuracao, JwtService jwt, Clock relogio, ServicoDeSessoes sessoes,
                      ServicoDeChamadaClassica chamadaClassica, PainelAoVivo painel, ServicoDeCheckin checkin,
                      ScheduledExecutorService agendador, MongoClient mongo, ObservadorDeMudancas observador)
    {
        this.configuracao = configuracao;
        this.jwt = jwt;
        this.relogio = relogio;
        this.sessoes = sessoes;
        this.chamadaClassica = chamadaClassica;
        this.painel = painel;
        this.checkin = checkin;
        this.agendador = agendador;
        this.mongo = mongo;
        this.observador = observador;
    }

    /** Monta os serviços e inicia os change streams. */
    public static Servicos montar (Configuracao configuracao)
    {
        // O servidor de sessão só valida tokens; a validade de emissão não é usada aqui
        JwtService jwt = new JwtService(configuracao.jwtSegredo(), Duration.ofHours(1));
        ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("sessao-agendador").factory());
        Clock relogio = Clock.systemUTC();

        ConnectionString uri = new ConnectionString(configuracao.mongoUri());
        MongoClient mongo = MongoClients.create(uri);
        MongoDatabase banco = mongo.getDatabase(uri.getDatabase());
        ServicoDeSessoes sessoes = new ServicoDeSessoes(banco, relogio, configuracao.duracaoMaxima());
        ServicoDeChamadaClassica chamadaClassica = new ServicoDeChamadaClassica(banco, sessoes, relogio);
        PainelAoVivo painel = new PainelAoVivo(banco, sessoes);
        ServicoDeCheckin checkin = new ServicoDeCheckin(banco, sessoes, relogio);
        ObservadorDeMudancas observador = new ObservadorDeMudancas(banco, painel);

        Servicos servicos = new Servicos(configuracao, jwt, relogio, sessoes, chamadaClassica, painel,
                checkin, agendador, mongo, observador);
        agendarEncerramentoPorTempoLimite(agendador, sessoes, configuracao.duracaoMaxima());
        observador.iniciar();
        return servicos;
    }

    /**
     * Varre periodicamente as sessões vencidas. Toda réplica faz a varredura; como a atualização só
     * altera sessões ainda abertas, cada sessão é encerrada uma única vez.
     */
    private static void agendarEncerramentoPorTempoLimite (ScheduledExecutorService agendador,
                                                           ServicoDeSessoes sessoes, Duration duracaoMaxima)
    {
        long intervalo = Math.clamp(duracaoMaxima.toMillis() / 4, 100, 30_000);
        agendador.scheduleWithFixedDelay(
                // Em virtual thread: com o MongoDB lento, a consulta não atrasa os prazos de autenticação
                () -> Thread.ofVirtual().name("sessao-tempo-limite").start(() -> {
                    try {
                        sessoes.encerrarVencidas();
                    } catch (RuntimeException e) {
                        System.getLogger(Servicos.class.getName())
                                .log(System.Logger.Level.WARNING, "Falha ao encerrar sessões vencidas", e);
                    }
                }),
                intervalo, intervalo, TimeUnit.MILLISECONDS);
    }

    public Configuracao configuracao ()
    {
        return this.configuracao;
    }

    public JwtService jwt ()
    {
        return this.jwt;
    }

    public Clock relogio ()
    {
        return this.relogio;
    }

    public ServicoDeSessoes sessoes ()
    {
        return this.sessoes;
    }

    public ServicoDeChamadaClassica chamadaClassica ()
    {
        return this.chamadaClassica;
    }

    public PainelAoVivo painel ()
    {
        return this.painel;
    }

    public ServicoDeCheckin checkin ()
    {
        return this.checkin;
    }

    public ScheduledExecutorService agendador ()
    {
        return this.agendador;
    }

    @Override
    public void close ()
    {
        this.observador.close();
        this.agendador.shutdownNow();
        this.mongo.close();
    }
}
