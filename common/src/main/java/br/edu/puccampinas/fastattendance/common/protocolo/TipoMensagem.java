/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.protocolo;

import br.edu.puccampinas.fastattendance.common.protocolo.dados.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.edu.puccampinas.fastattendance.common.protocolo.Direcao.CLIENTE_PARA_SERVIDOR;
import static br.edu.puccampinas.fastattendance.common.protocolo.Direcao.SERVIDOR_PARA_CLIENTE;

/** Tipos de mensagem do protocolo v1, com o nome usado no campo {@code tipo} e a classe de {@code dados}. */
public enum TipoMensagem {

    // Cliente → servidor
    AUTH("auth", CLIENTE_PARA_SERVIDOR, Auth.class),
    PING("ping", CLIENTE_PARA_SERVIDOR, Vazio.class),
    SESSAO_ABRIR("sessao.abrir", CLIENTE_PARA_SERVIDOR, SessaoAbrir.class),
    SESSAO_ENCERRAR("sessao.encerrar", CLIENTE_PARA_SERVIDOR, SessaoRef.class),
    SESSAO_ACOMPANHAR("sessao.acompanhar", CLIENTE_PARA_SERVIDOR, SessaoRef.class),
    SESSAO_TROCAR_MODO("sessao.trocar_modo", CLIENTE_PARA_SERVIDOR, SessaoTrocarModo.class),
    PRESENCA_MARCAR("presenca.marcar", CLIENTE_PARA_SERVIDOR, PresencaMarcar.class),
    PRESENCA_MARCAR_LOTE("presenca.marcar_lote", CLIENTE_PARA_SERVIDOR, PresencaMarcarLote.class),
    CHECKIN("checkin", CLIENTE_PARA_SERVIDOR, Checkin.class),

    // Servidor → cliente
    AUTH_OK("auth.ok", SERVIDOR_PARA_CLIENTE, AuthOk.class),
    PONG("pong", SERVIDOR_PARA_CLIENTE, Vazio.class),
    SESSAO_ABERTA("sessao.aberta", SERVIDOR_PARA_CLIENTE, SessaoAberta.class),
    SESSAO_ENCERRADA("sessao.encerrada", SERVIDOR_PARA_CLIENTE, SessaoEncerrada.class),
    SESSAO_ESTADO("sessao.estado", SERVIDOR_PARA_CLIENTE, SessaoEstado.class),
    SESSAO_MODO_ALTERADO("sessao.modo_alterado", SERVIDOR_PARA_CLIENTE, SessaoModoAlterado.class),
    PRESENCA_LOTE_OK("presenca.lote_ok", SERVIDOR_PARA_CLIENTE, PresencaLoteOk.class),
    PRESENCA_ATUALIZADA("presenca.atualizada", SERVIDOR_PARA_CLIENTE, PresencaAtualizada.class),
    CHECKIN_OK("checkin.ok", SERVIDOR_PARA_CLIENTE, CheckinOk.class),
    ERRO("erro", SERVIDOR_PARA_CLIENTE, Erro.class);

    private static final Map<String, TipoMensagem> POR_VALOR = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TipoMensagem::valor, Function.identity()));

    private final String valor;
    private final Direcao direcao;
    private final Class<?> classeDados;

    TipoMensagem(String valor, Direcao direcao, Class<?> classeDados) {
        this.valor = valor;
        this.direcao = direcao;
        this.classeDados = classeDados;
    }

    public String valor() {
        return valor;
    }

    public Direcao direcao() {
        return direcao;
    }

    public Class<?> classeDados() {
        return classeDados;
    }

    public static Optional<TipoMensagem> de(String valor) {
        return Optional.ofNullable(valor).map(POR_VALOR::get);
    }
}
