package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Relação da ponte com o servidor de sessão: fechamentos decididos pelo que o servidor faz (comunicado de
 * desligamento, queda, inatividade), e a correlação de {@code ref} com vários pedidos em sequência.
 */
class ServidorPonteTest extends CenarioSemBanco {

    @Test
    void comunicadoDeDesligamentoFecha1001() throws Exception {
        ClienteNavegador professor = professor();

        servidor.desativar();

        assertEquals(1001, professor.aguardarFechamento(5_000));
    }

    @Test
    void quedaDoServidorDepoisDeAutenticadoFecha1001() throws Exception {
        ClienteNavegador aluno = alunoConectado(ana);

        servidor.derrubarConexoes();

        // Sem comunicado, o TCP cai: autenticada, sem token vencido e com tráfego recente, a ponte deduz 1001
        assertEquals(1001, aluno.aguardarFechamento(5_000));
    }

    @Test
    void quedaAntesDeAutenticarFecha4001() throws Exception {
        ClienteNavegador cliente = conectar();
        // Espera a ponte abrir o socket ao servidor antes de derrubá-lo
        cliente.enviar(frame("ping", "p0", "{}"));
        cliente.proximo("pong");
        servidor.derrubarConexoes();

        assertEquals(4001, cliente.aguardarFechamento(5_000));
    }

    @Test
    void inatividadeDoServidorFecha1000() throws Exception {
        // Servidor e ponte com inatividade de 1,5 s: o servidor fecha por inatividade e a ponte deduz 1000
        try (ServidorDeTeste curto = new ServidorDeTeste(ServidorDeTeste.MONGO_INDISPONIVEL, Duration.ofSeconds(5),
                     Duration.ofMillis(1500));
             PonteDeTeste pontePropria = new PonteDeTeste(curto.porta(), Duration.ofSeconds(5),
                     Duration.ofMillis(1500))) {
            ClienteNavegador cliente = new ClienteNavegador(pontePropria.porta());
            try {
                cliente.enviar(frame("auth", "a1", "{\"token\":\"" + jwt.emitir(ana, "Ana", Papel.ALUNO).token() + "\"}"));
                cliente.proximo("auth.ok");

                assertEquals(1000, cliente.aguardarFechamento(10_000));
            } finally {
                cliente.close();
            }
        }
    }

    @Test
    void respostasDoServidorChegamNaOrdemDosPedidosComSeusRefs() throws Exception {
        ClienteNavegador cliente = autenticado(ana, Papel.ALUNO);

        // Respostas do próprio servidor (pong e erro de auth repetido), intercaladas: cada uma leva o id
        // do pedido que a originou, na ordem de envio
        cliente.enviar(frame("ping", "r1", "{}"));
        cliente.enviar(frame("auth", "r2", "{\"token\":\"x\"}"));
        cliente.enviar(frame("ping", "r3", "{}"));
        cliente.enviar(frame("auth", "r4", "{\"token\":\"x\"}"));
        cliente.enviar(frame("ping", "r5", "{}"));

        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            refs.add(cliente.proximo().get("ref").stringValue());
        }
        assertEquals(List.of("r1", "r2", "r3", "r4", "r5"), refs);
    }

    @Test
    void pedidosSemIdRecebemRespostaSemRef() throws Exception {
        ClienteNavegador cliente = autenticado(ana, Papel.ALUNO);

        cliente.enviar(frame("ping", null, "{}"));

        assertNull(cliente.proximo("pong").get("ref"));
    }
}
