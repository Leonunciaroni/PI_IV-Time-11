// Guilherme Preventi Correia
// RA: 25001694

import { ErroCheckin } from "./checkin";
import { listarSessoesAtivas } from "./api";
import type { CheckinOk } from "./protocolo";
import { ErroSessao, type ClienteSessao } from "./ws";

/** Confere novamente a sessão após captar o código, com prazo e cancelamento da tela. */
export async function verificarChamadaSom(
  token: string, turmaId: string, sessaoId: string, signal: AbortSignal,
): Promise<void> {
  if (signal.aborted) throw new DOMException("Operação cancelada", "AbortError");
  const consulta = new AbortController();
  const cancelar = () => consulta.abort();
  signal.addEventListener("abort", cancelar, { once: true });
  const limite = setTimeout(() => consulta.abort(), 10000);
  try {
    const ativas = await listarSessoesAtivas(token, consulta.signal);
    if (signal.aborted) throw new DOMException("Operação cancelada", "AbortError");
    if (!ativas.some((s) => s.sessaoId === sessaoId && s.turmaId === turmaId && s.modo === "SOM"))
      throw new ErroCheckin("CHAMADA_INDISPONIVEL", "Esta chamada por som não está mais disponível. Volte ao início e atualize suas turmas.");
  } catch (erro) {
    if (consulta.signal.aborted && !signal.aborted)
      throw new ErroCheckin("CONSULTA_DEMOROU", "O servidor demorou para verificar a chamada. Aguarde a conexão estabilizar e inicie uma nova escuta.");
    throw erro;
  } finally {
    clearTimeout(limite);
    signal.removeEventListener("abort", cancelar);
  }
}

/** O canal sonoro transmite só token e identidade da sessão/aparelho, sem coordenadas. */
export async function enviarCheckinSom(
  cliente: Pick<ClienteSessao, "estado" | "requisitar">,
  sessaoId: string,
  token: string,
  dispositivoId: string,
): Promise<CheckinOk> {
  if (!/^\d{6}$/.test(token) || !/^[a-f\d]{24}$/.test(sessaoId) || !dispositivoId)
    throw new ErroCheckin("MENSAGEM_INVALIDA", "Não foi possível identificar o sinal ou a chamada. Volte ao início e tente novamente.");
  if (cliente.estado !== "pronto")
    throw new ErroCheckin("SEM_CONEXAO", "A conexão com a chamada caiu. Aguarde reconectar e inicie uma nova escuta.");
  const resposta = await cliente.requisitar<CheckinOk>("checkin", { sessaoId, token, canal: "SOM", dispositivoId });
  if (!resposta || resposta.sessaoId !== sessaoId || !Number.isFinite(resposta.registradoEm) || resposta.registradoEm < 0)
    throw new ErroSessao("RESPOSTA_INVALIDA", "Não foi possível confirmar a resposta do servidor. Consulte seu histórico antes de tentar novamente.");
  return resposta;
}

export function mensagemDoSom(erro: unknown): { codigo: string; mensagem: string } {
  if (erro instanceof ErroSessao || erro instanceof ErroCheckin) {
    if (erro instanceof ErroSessao && erro.codigo === "SEM_CONEXAO")
      return { codigo: erro.codigo, mensagem: "A conexão caiu antes da confirmação. Confira o histórico depois que o professor encerrar a chamada antes de tentar novamente." };
    if (erro.codigo === "TOKEN_INVALIDO")
      return { codigo: erro.codigo, mensagem: "O sinal recebido não confere com esta chamada. Confira a turma e escute novamente o som do professor." };
    if (erro.codigo === "TOKEN_EXPIRADO")
      return { codigo: erro.codigo, mensagem: "O código sonoro expirou. Inicie uma nova escuta para receber o sinal atual do professor." };
    return { codigo: erro.codigo, mensagem: erro.message };
  }
  return { codigo: "ERRO_INTERNO", mensagem: "Não foi possível confirmar sua presença. Tente novamente ou procure o professor." };
}
