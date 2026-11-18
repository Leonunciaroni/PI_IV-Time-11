import { afterEach, describe, expect, it, vi } from "vitest";
import { enviarCheckinSom, mensagemDoSom, verificarChamadaSom } from "./checkinSom";
import { ErroSessao, type ClienteSessao } from "./ws";

const sessaoId = "000000000000000000000045";
function cliente(resposta: unknown = { sessaoId, registradoEm: 1000 }) {
  return { estado: "pronto" as ClienteSessao["estado"], requisitar: vi.fn().mockResolvedValue(resposta) };
}

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe("consulta da sessão ativa depois da captura", () => {
  it("consulta a API com JWT, identificando a sessão da própria turma", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify([{ sessaoId, turmaId: "turma", modo: "SOM" }])));
    vi.stubGlobal("fetch", fetch);
    await expect(verificarChamadaSom("jwt", "turma", sessaoId, new AbortController().signal)).resolves.toBeUndefined();
    expect(fetch).toHaveBeenCalledWith("/api/sessoes/ativas", expect.objectContaining({ cache: "no-store", headers: { Authorization: "Bearer jwt" } }));
  });
  it.each([{ sessoes: [] }, { sessoes: [{ sessaoId, turmaId: "outra", modo: "SOM" }] }, { sessoes: [{ sessaoId, turmaId: "turma", modo: "QRCODE" }] }])("recusa uma sessão encerrada, de outra turma ou de outro canal", async ({ sessoes }) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(sessoes))));
    await expect(verificarChamadaSom("jwt", "turma", sessaoId, new AbortController().signal)).rejects.toMatchObject({ codigo: "CHAMADA_INDISPONIVEL" });
  });
  it("interrompe uma consulta que fica sem resposta por dez segundos", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn((_url, opcoes: RequestInit) => new Promise((_resolve, reject) => {
      opcoes.signal!.addEventListener("abort", () => reject(new DOMException("Cancelado", "AbortError")));
    })));
    const resultado = verificarChamadaSom("jwt", "turma", sessaoId, new AbortController().signal);
    const falha = expect(resultado).rejects.toMatchObject({ codigo: "CONSULTA_DEMOROU" });
    await vi.advanceTimersByTimeAsync(10000); await falha;
    expect(vi.getTimerCount()).toBe(0);
  });
  it("não consulta quando a tela já saiu", async () => {
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch); const controle = new AbortController(); controle.abort();
    await expect(verificarChamadaSom("jwt", "turma", sessaoId, controle.signal)).rejects.toMatchObject({ name: "AbortError" });
    expect(fetch).not.toHaveBeenCalled();
  });
});

describe("check-in por som", () => {
  it("envia o token com zeros e o aparelho sem GPS, áudio ou JWT no payload", async () => {
    const conexao = cliente();
    await expect(enviarCheckinSom(conexao, sessaoId, "001234", "aparelho")).resolves.toEqual({ sessaoId, registradoEm: 1000 });
    expect(conexao.requisitar).toHaveBeenCalledExactlyOnceWith("checkin", { sessaoId, token: "001234", canal: "SOM", dispositivoId: "aparelho" });
  });
  it("não enfileira um código para envio depois da reconexão", async () => {
    const conexao = cliente(); conexao.estado = "reconectando";
    await expect(enviarCheckinSom(conexao, sessaoId, "001234", "aparelho")).rejects.toMatchObject({ codigo: "SEM_CONEXAO" });
    expect(conexao.requisitar).not.toHaveBeenCalled();
  });
  it.each(["12345", "1234567", "12a456", "", " 12345"])("recusa token inválido %s sem enviar", async (token) => {
    const conexao = cliente();
    await expect(enviarCheckinSom(conexao, sessaoId, token, "aparelho")).rejects.toMatchObject({ codigo: "MENSAGEM_INVALIDA" });
    expect(conexao.requisitar).not.toHaveBeenCalled();
  });
  it.each([null, { sessaoId: "outra", registradoEm: 1000 }, { sessaoId, registradoEm: "1000" }, { sessaoId, registradoEm: NaN }, { sessaoId, registradoEm: -1 }])("não confirma uma resposta inválida", async (resposta) => {
    await expect(enviarCheckinSom(cliente(resposta), sessaoId, "001234", "aparelho")).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
  it("preserva a recusa do servidor sem repetir o envio", async () => {
    const conexao = cliente();
    conexao.requisitar.mockRejectedValue(new ErroSessao("PRESENCA_DUPLICADA", "Já registrada."));
    await expect(enviarCheckinSom(conexao, sessaoId, "001234", "aparelho")).rejects.toMatchObject({ codigo: "PRESENCA_DUPLICADA" });
    expect(conexao.requisitar).toHaveBeenCalledTimes(1);
  });
  it.each(["TOKEN_INVALIDO", "TOKEN_EXPIRADO"])("orienta nova escuta para %s, sem mencionar câmera ou QR", (codigo) => {
    const mensagem = mensagemDoSom(new ErroSessao(codigo, "Leia o QR novamente."));
    expect(mensagem.codigo).toBe(codigo);
    expect(mensagem.mensagem).not.toMatch(/QR|câmera/);
    expect(mensagem.mensagem).toMatch(/escut/);
  });
  it("trata perda de conexão depois do envio como resultado incerto", () => {
    expect(mensagemDoSom(new ErroSessao("SEM_CONEXAO", "Sem conexão")).mensagem).toMatch(/histórico/);
  });
});
