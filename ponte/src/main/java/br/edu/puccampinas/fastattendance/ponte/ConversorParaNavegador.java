/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.ConfiguracaoTotp;
import br.edu.puccampinas.fastattendance.common.comunicado.Erro;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDeModoAlterado;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDePresencaAtualizada;
import br.edu.puccampinas.fastattendance.common.comunicado.EventoDeSessaoEncerrada;
import br.edu.puccampinas.fastattendance.common.comunicado.PresenteResumo;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeMarcarPresencaEmLote;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDePong;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeTrocarModo;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converte um comunicado do servidor de sessão no frame JSON do protocolo v1 (docs/protocolo-v1.md,
 * seções 2 e 5). O envelope {@code v}, {@code tipo}, {@code ref} e {@code ts} é montado aqui.
 * <p>
 * {@code ref} é o {@code id} da requisição, para respostas e erros. Eventos e erros sem requisição
 * (frame binário, falha interna) saem sem {@code ref}, passando {@code null}.
 */
final class ConversorParaNavegador {

    private final Json json;

    ConversorParaNavegador(Json json) {
        this.json = json;
    }

    /** Frame JSON de um comunicado; {@code ref} {@code null} para eventos. */
    String paraFrame(Comunicado comunicado, String ref) {
        String tipo;
        Map<String, Object> dados;
        if (comunicado instanceof RespostaDeAutenticacao r) {
            tipo = TipoMensagem.AUTH_OK.valor();
            dados = mapa("usuarioId", r.getUsuarioId(), "nome", r.getNome(), "papel", r.getPapel().name(),
                    "expiraEm", r.getExpiraEm());
        } else if (comunicado instanceof RespostaDePong) {
            tipo = TipoMensagem.PONG.valor();
            dados = new LinkedHashMap<>();
        } else if (comunicado instanceof RespostaDeAbrirSessao r) {
            tipo = TipoMensagem.SESSAO_ABERTA.valor();
            dados = mapa("sessaoId", r.getSessaoId(), "turmaId", r.getTurmaId(), "modo", r.getModo().name(),
                    "abertaEm", r.getAbertaEm(), "totp", totp(r.getTotp()));
        } else if (comunicado instanceof RespostaDeEncerrarSessao r) {
            tipo = TipoMensagem.SESSAO_ENCERRADA.valor();
            dados = mapa("sessaoId", r.getSessaoId(), "motivo", r.getMotivo().name(),
                    "encerradaEm", r.getEncerradaEm(), "totalPresentes", r.getTotalPresentes());
        } else if (comunicado instanceof EventoDeSessaoEncerrada r) {
            tipo = TipoMensagem.SESSAO_ENCERRADA.valor();
            dados = mapa("sessaoId", r.getSessaoId(), "motivo", r.getMotivo().name(),
                    "encerradaEm", r.getEncerradaEm(), "totalPresentes", r.getTotalPresentes());
        } else if (comunicado instanceof RespostaDeTrocarModo r) {
            tipo = TipoMensagem.SESSAO_MODO_ALTERADO.valor();
            dados = modoAlterado(r.getSessaoId(), r.getModo(), r.getSalaId(), r.getAlteradoEm());
        } else if (comunicado instanceof EventoDeModoAlterado r) {
            tipo = TipoMensagem.SESSAO_MODO_ALTERADO.valor();
            dados = modoAlterado(r.getSessaoId(), r.getModo(), r.getSalaId(), r.getAlteradoEm());
        } else if (comunicado instanceof RespostaDeAcompanharSessao r) {
            tipo = TipoMensagem.SESSAO_ESTADO.valor();
            dados = mapa("sessaoId", r.getSessaoId(), "modo", r.getModo().name(), "abertaEm", r.getAbertaEm(),
                    "presentes", presentes(r.getPresentes()), "totalMatriculados", r.getTotalMatriculados());
        } else if (comunicado instanceof RespostaDeMarcarPresenca r) {
            tipo = TipoMensagem.PRESENCA_ATUALIZADA.valor();
            dados = presenca(r.getSessaoId(), r.getAlunoId(), r.getNomeAluno(), r.isPresente(), r.getOrigem(),
                    r.getRegistradoEm(), r.getTotalPresentes(), r.getTotalMatriculados());
        } else if (comunicado instanceof EventoDePresencaAtualizada r) {
            tipo = TipoMensagem.PRESENCA_ATUALIZADA.valor();
            dados = presenca(r.getSessaoId(), r.getAlunoId(), r.getNomeAluno(), r.isPresente(), r.getOrigem(),
                    r.getRegistradoEm(), r.getTotalPresentes(), r.getTotalMatriculados());
        } else if (comunicado instanceof RespostaDeMarcarPresencaEmLote r) {
            tipo = TipoMensagem.PRESENCA_LOTE_OK.valor();
            dados = mapa("sessaoId", r.getSessaoId(), "totalPresentes", r.getTotalPresentes(),
                    "totalMatriculados", r.getTotalMatriculados());
        } else if (comunicado instanceof RespostaDeCheckin r) {
            tipo = TipoMensagem.CHECKIN_OK.valor();
            dados = mapa("sessaoId", r.getSessaoId(), "registradoEm", r.getRegistradoEm());
        } else if (comunicado instanceof Erro r) {
            tipo = TipoMensagem.ERRO.valor();
            dados = mapa("codigo", r.getCodigo().name(), "mensagem", r.getMensagem());
        } else {
            throw new IllegalArgumentException("Comunicado sem tradução para o protocolo v1: " + comunicado);
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("v", Protocolo.VERSAO);
        envelope.put("tipo", tipo);
        if (ref != null) {
            envelope.put("ref", ref);
        }
        envelope.put("ts", System.currentTimeMillis());
        envelope.put("dados", dados);
        return json.escrever(envelope);
    }

    /** {@code salaId} sai como {@code null} quando a sessão não tem sala, para o campo estar sempre presente. */
    private static Map<String, Object> modoAlterado(String sessaoId, ModoChamada modo, String salaId, long alteradoEm) {
        return mapa("sessaoId", sessaoId, "modo", modo.name(), "salaId", salaId, "alteradoEm", alteradoEm);
    }

    private static Map<String, Object> totp(ConfiguracaoTotp totp) {
        return mapa("segredo", totp.getSegredo(), "algoritmo", totp.getAlgoritmo(), "digitos", totp.getDigitos(),
                "periodoSegundos", totp.getPeriodoSegundos(), "t0", totp.getT0());
    }

    private static List<Map<String, Object>> presentes(List<PresenteResumo> presentes) {
        return presentes.stream()
                .map(p -> mapa("alunoId", p.getAlunoId(), "nome", p.getNome(), "origem", p.getOrigem().name(),
                        "registradoEm", p.getRegistradoEm()))
                .toList();
    }

    private static Map<String, Object> presenca(String sessaoId, String alunoId, String nomeAluno, boolean presente,
                                                OrigemPresenca origem, long registradoEm, int totalPresentes,
                                                int totalMatriculados) {
        return mapa("sessaoId", sessaoId, "alunoId", alunoId, "nomeAluno", nomeAluno, "presente", presente,
                "origem", origem.name(), "registradoEm", registradoEm,
                "totalPresentes", totalPresentes, "totalMatriculados", totalMatriculados);
    }

    /** Mapa que preserva a ordem dos pares, para o JSON sair com os campos na ordem da documentação. */
    private static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }
}
