package br.edu.puccampinas.fastattendance.sessao.protocolo;

import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.protocolo.Direcao;
import br.edu.puccampinas.fastattendance.common.protocolo.Envelope;
import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Auth;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.AuthOk;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Erro;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Vazio;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.common.seguranca.TokenInvalidoException;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
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
 * Nesta versão são tratados {@code auth} e {@code ping}. Os demais tipos do protocolo são
 * reconhecidos, exigem autenticação e respondem que ainda não estão disponíveis; serão
 * implementados nas próximas semanas do cronograma.
 */
public final class RoteadorDeMensagens implements ProcessadorDeMensagens {

    private static final int TAMANHO_MAXIMO_ID = 64;

    private final Json json;
    private final JwtService jwt;
    private final ScheduledExecutorService agendador;
    private final Clock relogio;

    public RoteadorDeMensagens(Json json, JwtService jwt, ScheduledExecutorService agendador, Clock relogio) {
        this.json = json;
        this.jwt = jwt;
        this.agendador = agendador;
        this.relogio = relogio;
    }

    @Override
    public void processar(Conexao conexao, String texto) {
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
                } else {
                    erro(conexao, id, CodigoErro.MENSAGEM_INVALIDA,
                            "Tipo " + nomeTipo + " ainda não disponível nesta versão do servidor");
                }
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

    private static void erro(Conexao conexao, String ref, CodigoErro codigo, String mensagem) {
        conexao.enviar(Envelope.resposta(TipoMensagem.ERRO, ref, new Erro(codigo, mensagem)));
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor != null && valor.isString() ? valor.stringValue() : null;
    }
}
