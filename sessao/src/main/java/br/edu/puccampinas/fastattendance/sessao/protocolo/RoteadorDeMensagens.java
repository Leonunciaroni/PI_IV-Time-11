package br.edu.puccampinas.fastattendance.sessao.protocolo;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.protocolo.Direcao;
import br.edu.puccampinas.fastattendance.common.protocolo.Envelope;
import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Auth;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.AuthOk;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Checkin;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Erro;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresencaMarcar;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresencaMarcarLote;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.SessaoAbrir;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.SessaoRef;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Vazio;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.common.seguranca.TokenInvalidoException;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeCheckin;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeChamadaClassica;
import br.edu.puccampinas.fastattendance.sessao.chamada.ServicoDeSessoes;
import br.edu.puccampinas.fastattendance.sessao.servidor.Conexao;
import br.edu.puccampinas.fastattendance.sessao.servidor.ProcessadorDeMensagens;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Valida o envelope (docs/protocolo-v1.md, seção 2) e despacha cada tipo de mensagem.
 * <p>
 * Nesta versão são tratados {@code auth}, {@code ping}, {@code sessao.abrir},
 * {@code sessao.encerrar}, {@code presenca.marcar}, {@code presenca.marcar_lote} e {@code checkin}
 * (canal QR Code). Os demais tipos do protocolo são reconhecidos, exigem autenticação e recusam
 * sessões encerradas, mas ainda respondem que não estão disponíveis; serão implementados nas
 * próximas semanas do cronograma.
 * <p>
 * Antes de validar o envelope, cada mensagem passa pelo limite de mensagens por conexão
 * ({@code LIMITE_EXCEDIDO}).
 */
public final class RoteadorDeMensagens implements ProcessadorDeMensagens {

    private static final int TAMANHO_MAXIMO_ID = 64;

    private final Json json;
    private final JwtService jwt;
    private final ScheduledExecutorService agendador;
    private final Clock relogio;
    private final ServicoDeSessoes sessoes;
    private final ServicoDeChamadaClassica chamadaClassica;
    private final ServicoDeCheckin checkin;

    public RoteadorDeMensagens(Json json, JwtService jwt, ScheduledExecutorService agendador, Clock relogio,
                               ServicoDeSessoes sessoes, ServicoDeChamadaClassica chamadaClassica,
                               ServicoDeCheckin checkin) {
        this.json = json;
        this.jwt = jwt;
        this.agendador = agendador;
        this.relogio = relogio;
        this.sessoes = sessoes;
        this.chamadaClassica = chamadaClassica;
        this.checkin = checkin;
    }

    @Override
    public void processar(Conexao conexao, String texto) {
        // Antes de tudo, inclusive antes da validação do envelope: a conta é de mensagens recebidas
        if (!conexao.limitador().registrar()) {
            erro(conexao, idSeLegivel(texto), CodigoErro.LIMITE_EXCEDIDO, "Mensagens demais em pouco tempo");
            if (conexao.limitador().excessoRepetido()) {
                conexao.fechar(CodigoFechamento.VIOLACAO_POLITICA, "Limite de mensagens excedido repetidamente");
            }
            return;
        }

        JsonNode mensagem;
        try {
            mensagem = json.ler(texto);
        } catch (JacksonException e) {
            erro(conexao, null, CodigoErro.MENSAGEM_INVALIDA, "JSON malformado");
            return;
        }
        if (mensagem == null || !mensagem.isObject()) {
            erro(conexao, null, CodigoErro.MENSAGEM_INVALIDA, "A mensagem deve ser um objeto JSON");
            return;
        }

        String id = texto(mensagem, "id");
        if (id != null && id.length() > TAMANHO_MAXIMO_ID) {
            erro(conexao, null, CodigoErro.MENSAGEM_INVALIDA, "Campo id excede " + TAMANHO_MAXIMO_ID + " caracteres");
            return;
        }

        JsonNode versao = mensagem.get("v");
        if (versao == null || !versao.isInt()) {
            erro(conexao, id, CodigoErro.MENSAGEM_INVALIDA, "Campo v ausente ou inválido");
            return;
        }
        if (versao.intValue() != Protocolo.VERSAO) {
            erro(conexao, id, CodigoErro.VERSAO_NAO_SUPORTADA, "Versão suportada: " + Protocolo.VERSAO);
            conexao.fechar(CodigoFechamento.VERSAO_NAO_SUPORTADA, "Versão de protocolo não suportada");
            return;
        }

        String nomeTipo = texto(mensagem, "tipo");
        Optional<TipoMensagem> tipo = TipoMensagem.de(nomeTipo)
                .filter(t -> t.direcao() == Direcao.CLIENTE_PARA_SERVIDOR);
        if (tipo.isEmpty()) {
            erro(conexao, id, CodigoErro.MENSAGEM_INVALIDA, "Tipo desconhecido: " + nomeTipo);
            return;
        }

        JsonNode dados = mensagem.get("dados");
        if (dados != null && !dados.isObject() && !dados.isNull()) {
            erro(conexao, id, CodigoErro.MENSAGEM_INVALIDA, "Campo dados deve ser um objeto");
            return;
        }
        if (dados == null || dados.isNull()) {
            dados = json.ler("{}");
        }

        switch (tipo.get()) {
            case PING -> conexao.enviar(Envelope.resposta(TipoMensagem.PONG, id, new Vazio()));
            case AUTH -> autenticar(conexao, id, dados);
            default -> {
                if (!conexao.autenticada()) {
                    erro(conexao, id, CodigoErro.AUTH_REQUERIDA, "Envie auth antes de " + nomeTipo);
                    return;
                }
                try {
                    despachar(conexao, tipo.get(), id, dados);
                } catch (ErroDeProtocolo e) {
                    erro(conexao, id, e.codigo(), e.getMessage());
                } catch (JacksonException e) {
                    erro(conexao, id, CodigoErro.MENSAGEM_INVALIDA, "Campos inválidos em dados");
                }
            }
        }
    }

    /** Mensagens que exigem autenticação. */
    private void despachar(Conexao conexao, TipoMensagem tipo, String id, JsonNode dados) {
        UsuarioAutenticado usuario = conexao.usuario();
        switch (tipo) {
            case SESSAO_ABRIR -> conexao.enviar(Envelope.resposta(TipoMensagem.SESSAO_ABERTA, id,
                    sessoes.abrir(usuario, json.converter(dados, SessaoAbrir.class))));
            case SESSAO_ENCERRAR -> conexao.enviar(Envelope.resposta(TipoMensagem.SESSAO_ENCERRADA, id,
                    sessoes.encerrar(usuario, json.converter(dados, SessaoRef.class).sessaoId())));
            case PRESENCA_MARCAR -> {
                exigirBooleano(dados, "presente");
                conexao.enviar(Envelope.resposta(TipoMensagem.PRESENCA_ATUALIZADA, id,
                        chamadaClassica.marcar(usuario, json.converter(dados, PresencaMarcar.class))));
            }
            case PRESENCA_MARCAR_LOTE -> {
                exigirBooleano(dados, "presente");
                conexao.enviar(Envelope.resposta(TipoMensagem.PRESENCA_LOTE_OK, id,
                        chamadaClassica.marcarLote(usuario, json.converter(dados, PresencaMarcarLote.class))));
            }
            case CHECKIN -> conexao.enviar(Envelope.resposta(TipoMensagem.CHECKIN_OK, id,
                    checkin.registrar(usuario, json.converter(dados, Checkin.class))));
            default -> {
                // Todo registro passa por aqui primeiro: sessão encerrada não aceita mais nada
                sessoes.aberta(texto(dados, "sessaoId"));
                erro(conexao, id, CodigoErro.MENSAGEM_INVALIDA,
                        "Tipo " + tipo.valor() + " ainda não disponível nesta versão do servidor");
            }
        }
    }

    private void autenticar(Conexao conexao, String id, JsonNode dados) {
        if (conexao.autenticada()) {
            erro(conexao, id, CodigoErro.MENSAGEM_INVALIDA, "Conexão já autenticada");
            return;
        }
        UsuarioAutenticado usuario;
        try {
            usuario = jwt.validar(json.converter(dados, Auth.class).token());
        } catch (TokenInvalidoException | JacksonException e) {
            erro(conexao, id, CodigoErro.AUTH_INVALIDA, "Token inválido ou expirado");
            conexao.fechar(CodigoFechamento.AUTENTICACAO_FALHOU, "Autenticação inválida");
            return;
        }

        conexao.autenticar(usuario);
        long restante = Math.max(0, Duration.between(relogio.instant(), usuario.expiraEm()).toMillis());
        conexao.vincular(agendador.schedule(
                () -> conexao.fechar(CodigoFechamento.TOKEN_EXPIROU, "Token expirado"),
                restante, TimeUnit.MILLISECONDS));

        conexao.enviar(Envelope.resposta(TipoMensagem.AUTH_OK, id,
                new AuthOk(usuario.id(), usuario.nome(), usuario.papel(), usuario.expiraEm().toEpochMilli())));
    }

    /** Campos booleanos são obrigatórios: ausentes, seriam lidos como {@code false} sem aviso. */
    private static void exigirBooleano(JsonNode dados, String campo) {
        JsonNode valor = dados.get(campo);
        if (valor == null || !valor.isBoolean()) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Campo " + campo + " deve ser true ou false");
        }
    }

    private static void erro(Conexao conexao, String ref, CodigoErro codigo, String mensagem) {
        conexao.enviar(Envelope.resposta(TipoMensagem.ERRO, ref, new Erro(codigo, mensagem)));
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor != null && valor.isString() ? valor.stringValue() : null;
    }

    /** Id da mensagem para correlacionar a resposta de erro; {@code null} se o texto não for um objeto JSON. */
    private String idSeLegivel(String texto) {
        try {
            JsonNode mensagem = json.ler(texto);
            return mensagem != null && mensagem.isObject() ? texto(mensagem, "id") : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
