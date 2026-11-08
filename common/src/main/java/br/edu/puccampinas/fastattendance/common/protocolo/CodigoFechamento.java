package br.edu.puccampinas.fastattendance.common.protocolo;

/** Códigos de fechamento do WebSocket (seção 8 do protocolo). */
public final class CodigoFechamento {

    public static final int NORMAL = 1000;
    public static final int SERVIDOR_REINICIANDO = 1001;
    public static final int AUTENTICACAO_FALHOU = 4001;
    public static final int VERSAO_NAO_SUPORTADA = 4002;
    public static final int TOKEN_EXPIROU = 4003;
    public static final int VIOLACAO_POLITICA = 4008;

    private CodigoFechamento() {
    }
}
