/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresencaEmLote;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDePing;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeTrocarModo;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoFechamento;
import br.edu.puccampinas.fastattendance.common.protocolo.Direcao;
import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte um frame de texto do navegador (protocolo v1, docs/protocolo-v1.md, seções 2 e 4) em um
 * comunicado do servidor de sessão. Recusa com {@link FalhaDeProtocolo} o que a ponte valida: JSON,
 * envelope, versão, tipo, {@code dados}, {@code id} longo, {@code presente} não booleano, modo e canal
 * desconhecidos, e pedido antes da autenticação. As regras de negócio ficam no servidor.
 * <p>
 * A ordem das verificações segue o antigo {@code RoteadorDeMensagens}, para o navegador receber o mesmo
 * erro de antes quando um frame tem mais de um problema.
 */
final class ConversorDeFrames {

    static final int TAMANHO_MAXIMO_ID = 64;

    private final Json json;

    ConversorDeFrames(Json json) {
        this.json = json;
    }

    /** Um frame aceito: o tipo, o {@code id} para a correlação (ou {@code null}) e o comunicado a encaminhar. */
    record Recebido(TipoMensagem tipo, String id, Comunicado comunicado) {
    }

    Recebido analisar(String texto, boolean autenticacaoEnviada) {
        JsonNode mensagem = lerObjeto(texto);

        String id = texto(mensagem, "id");
        if (id != null && id.length() > TAMANHO_MAXIMO_ID) {
            throw new FalhaDeProtocolo(CodigoErro.MENSAGEM_INVALIDA,
                    "Campo id excede " + TAMANHO_MAXIMO_ID + " caracteres", null);
        }

        JsonNode versao = mensagem.get("v");
        if (versao == null || !versao.isInt()) {
            throw new FalhaDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Campo v ausente ou inválido", id);
        }
        if (versao.intValue() != Protocolo.VERSAO) {
            throw new FalhaDeProtocolo(CodigoErro.VERSAO_NAO_SUPORTADA,
                    "Versão suportada: " + Protocolo.VERSAO, id, CodigoFechamento.VERSAO_NAO_SUPORTADA);
        }

        String nomeTipo = texto(mensagem, "tipo");
        TipoMensagem tipo = TipoMensagem.de(nomeTipo)
                .filter(t -> t.direcao() == Direcao.CLIENTE_PARA_SERVIDOR)
                .orElseThrow(() -> new FalhaDeProtocolo(CodigoErro.MENSAGEM_INVALIDA,
                        "Tipo desconhecido ou que o cliente não pode enviar: " + nomeTipo, id));

        JsonNode dados = mensagem.get("dados");
        if (dados == null || dados.isNull()) {
            dados = json.ler("{}");
        } else if (!dados.isObject()) {
            throw new FalhaDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Campo dados deve ser um objeto", id);
        }

        // Pedido de negócio antes de auth: o servidor também recusaria, mas só depois de validar os campos
        boolean semAutenticacao = !autenticacaoEnviada && tipo != TipoMensagem.AUTH && tipo != TipoMensagem.PING;
        if (semAutenticacao) {
            throw new FalhaDeProtocolo(CodigoErro.AUTH_REQUERIDA, "Envie auth antes de " + nomeTipo, id);
        }

        try {
            return new Recebido(tipo, id, montar(tipo, dados));
        } catch (IllegalArgumentException campo) {
            throw new FalhaDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, campo.getMessage(), id);
        }
    }

    /** Id do frame para correlacionar um erro, quando o texto é um objeto JSON com {@code id} texto. */
    String idSeLegivel(String texto) {
        try {
            JsonNode mensagem = json.ler(texto);
            return mensagem != null && mensagem.isObject() ? texto(mensagem, "id") : null;
        } catch (JacksonException falha) {
            return null;
        }
    }

    private Comunicado montar(TipoMensagem tipo, JsonNode dados) {
        return switch (tipo) {
            case PING -> new PedidoDePing();
            case AUTH -> new PedidoDeAutenticacao(texto(dados, "token"));
            case SESSAO_ABRIR -> new PedidoDeAbrirSessao(textoOuNulo(dados, "turmaId"),
                    modo(textoOuNulo(dados, "modo")), textoOuNulo(dados, "salaId"));
            case SESSAO_ENCERRAR -> new PedidoDeEncerrarSessao(textoOuNulo(dados, "sessaoId"));
            case SESSAO_ACOMPANHAR -> new PedidoDeAcompanharSessao(textoOuNulo(dados, "sessaoId"));
            case SESSAO_TROCAR_MODO -> new PedidoDeTrocarModo(textoOuNulo(dados, "sessaoId"),
                    modo(textoOuNulo(dados, "modo")), textoOuNulo(dados, "salaId"));
            case PRESENCA_MARCAR -> new PedidoDeMarcarPresenca(textoOuNulo(dados, "sessaoId"),
                    textoOuNulo(dados, "alunoId"), booleano(dados, "presente"));
            case PRESENCA_MARCAR_LOTE -> new PedidoDeMarcarPresencaEmLote(textoOuNulo(dados, "sessaoId"),
                    booleano(dados, "presente"), excecoes(dados));
            case CHECKIN -> new PedidoDeCheckin(textoOuNulo(dados, "sessaoId"), textoOuNulo(dados, "token"),
                    canal(textoOuNulo(dados, "canal")), textoOuNulo(dados, "dispositivoId"),
                    numeroOuNulo(dados, "latitude"), numeroOuNulo(dados, "longitude"),
                    numeroOuNulo(dados, "precisaoMetros"));
            default -> throw new IllegalStateException("Tipo que não vai ao servidor: " + tipo);
        };
    }

    private static ModoChamada modo(String valor) {
        if (valor == null) {
            // Ausente: o servidor responde MENSAGEM_INVALIDA, como em turmaId ausente
            return null;
        }
        try {
            return ModoChamada.valueOf(valor);
        } catch (IllegalArgumentException desconhecido) {
            throw new IllegalArgumentException("Modo desconhecido: " + valor);
        }
    }

    private static OrigemPresenca canal(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return OrigemPresenca.valueOf(valor);
        } catch (IllegalArgumentException desconhecido) {
            throw new IllegalArgumentException("Canal desconhecido: " + valor);
        }
    }

    private static List<String> excecoes(JsonNode dados) {
        // Sempre um ArrayList: o servidor de sessão só desserializa essa classe entre as coleções
        // (FiltroDeComunicados), e List.of() chegaria recusado, derrubando a conexão.
        JsonNode no = dados.get("excecoes");
        if (no == null || no.isNull()) {
            return new ArrayList<>();
        }
        if (!no.isArray()) {
            throw new IllegalArgumentException("Campo excecoes deve ser uma lista de textos");
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode item : no) {
            if (!item.isString()) {
                throw new IllegalArgumentException("Campo excecoes deve ser uma lista de textos");
            }
            ids.add(item.stringValue());
        }
        return ids;
    }

    private static boolean booleano(JsonNode dados, String campo) {
        JsonNode no = dados.get(campo);
        if (no == null || !no.isBoolean()) {
            throw new IllegalArgumentException("Campo " + campo + " deve ser true ou false");
        }
        return no.booleanValue();
    }

    private static Double numeroOuNulo(JsonNode dados, String campo) {
        JsonNode no = dados.get(campo);
        if (no == null || no.isNull()) {
            return null;
        }
        if (!no.isNumber()) {
            throw new IllegalArgumentException("Campo " + campo + " deve ser um número");
        }
        return no.doubleValue();
    }

    /** Texto do campo, ou {@code null} se ausente, nulo ou de outro tipo (campo lido de forma livre). */
    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor != null && valor.isString() ? valor.stringValue() : null;
    }

    /** Texto do campo, ou {@code null} se ausente ou nulo; outro tipo é {@code MENSAGEM_INVALIDA}. */
    private static String textoOuNulo(JsonNode dados, String campo) {
        JsonNode valor = dados.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isString()) {
            throw new IllegalArgumentException("Campo " + campo + " deve ser um texto");
        }
        return valor.stringValue();
    }

    private JsonNode lerObjeto(String texto) {
        JsonNode mensagem;
        try {
            mensagem = json.ler(texto);
        } catch (JacksonException falha) {
            throw new FalhaDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "JSON malformado", null);
        }
        if (mensagem == null || !mensagem.isObject()) {
            throw new FalhaDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "A mensagem deve ser um objeto JSON", null);
        }
        return mensagem;
    }
}
