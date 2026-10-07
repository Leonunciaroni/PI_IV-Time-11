package br.edu.puccampinas.fastattendance.sessao;

/**
 * Ponto de entrada do servidor de sessão em tempo real.
 * <p>
 * Este módulo não usa framework: o caminho crítico de check-in precisa de latência baixa e
 * previsível. O servidor WebSocket é implementado na semana 2 do cronograma.
 */
public final class SessaoApplication {

    private SessaoApplication() {
    }

    public static void main(String[] args) {
        System.out.println("Fast Attendance — servidor de sessão");
    }
}
