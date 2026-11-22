import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import origem from "./origem.json";
import { escutarToken, mensagemDoMicrofone, type OpcoesReceptor } from "./receptor";

function equipamento(iniciar = vi.fn().mockResolvedValue(true)) {
  let callbacks: OpcoesReceptor;
  const parar = vi.fn().mockResolvedValue(undefined);
  const criar = vi.fn((opcoes: OpcoesReceptor) => { callbacks = opcoes; return { iniciar, parar }; });
  return { fabrica: { criar }, criar, iniciar, parar, token: (token: string) => callbacks.aoToken(token), erro: (erro: unknown) => callbacks.aoErro(erro) };
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal("isSecureContext", true);
  vi.stubGlobal("navigator", { mediaDevices: { getUserMedia: vi.fn() } });
  for (const nome of ["AudioContext", "AudioWorkletNode", "Worker"]) vi.stubGlobal(nome, class {});
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

describe("recepção do primeiro token", () => {
  it("inicia dentro do gesto e fecha a captura antes de devolver o primeiro token", async () => {
    const e = equipamento(); const pronto = vi.fn();
    const recebido = escutarToken(e.fabrica, new AbortController().signal, pronto);
    expect(e.iniciar).toHaveBeenCalledTimes(1);
    await Promise.resolve(); expect(pronto).toHaveBeenCalledTimes(1);
    e.token("001234"); e.token("999999");
    expect(e.parar).toHaveBeenCalledTimes(1);
    await expect(recebido).resolves.toBe("001234");
    expect(vi.getTimerCount()).toBe(0);
  });
  it("ignora eventos sem um token de seis dígitos", async () => {
    const e = equipamento(); const recebido = escutarToken(e.fabrica, new AbortController().signal, vi.fn());
    e.token("ruído"); expect(e.parar).not.toHaveBeenCalled();
    e.token("001234"); await expect(recebido).resolves.toBe("001234");
  });
  it("cancela a inicialização e ignora a concessão tardia da permissão", async () => {
    let liberar!: (valor: boolean) => void;
    const e = equipamento(vi.fn(() => new Promise<boolean>(resolve => { liberar = resolve; })));
    const controle = new AbortController(); const pronto = vi.fn();
    const recebido = escutarToken(e.fabrica, controle.signal, pronto);
    const falha = expect(recebido).rejects.toMatchObject({ name: "AbortError" });
    controle.abort(); liberar(true); await falha; await Promise.resolve();
    expect(e.parar).toHaveBeenCalledTimes(1); expect(pronto).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("não solicita equipamento quando a operação já foi cancelada", async () => {
    const e = equipamento(); const controle = new AbortController(); controle.abort();
    await expect(escutarToken(e.fabrica, controle.signal, vi.fn())).rejects.toMatchObject({ name: "AbortError" });
    expect(e.criar).not.toHaveBeenCalled();
  });
  it("recusa HTTP inseguro antes de abrir o microfone", async () => {
    vi.stubGlobal("isSecureContext", false); const e = equipamento();
    await expect(escutarToken(e.fabrica, new AbortController().signal, vi.fn())).rejects.toMatchObject({ codigo: "CONTEXTO_INSEGURO" });
    expect(e.criar).not.toHaveBeenCalled();
  });
  it("recusa navegador sem AudioWorklet antes de capturar", async () => {
    vi.stubGlobal("AudioWorkletNode", undefined); const e = equipamento();
    await expect(escutarToken(e.fabrica, new AbortController().signal, vi.fn())).rejects.toMatchObject({ codigo: "AUDIO_INCOMPATIVEL" });
    expect(e.criar).not.toHaveBeenCalled();
  });
  it("limita a espera da permissão sem deixar a captura ativa", async () => {
    const e = equipamento(vi.fn(() => new Promise<boolean>(() => {})));
    const recebido = escutarToken(e.fabrica, new AbortController().signal, vi.fn());
    const falha = expect(recebido).rejects.toMatchObject({ codigo: "MICROFONE_DEMOROU" });
    await vi.advanceTimersByTimeAsync(20000); await falha; expect(e.parar).toHaveBeenCalledTimes(1);
  });
  it("encerra a escuta sem sinal após 45 segundos", async () => {
    const e = equipamento(); const recebido = escutarToken(e.fabrica, new AbortController().signal, vi.fn());
    const falha = expect(recebido).rejects.toMatchObject({ codigo: "SINAL_NAO_RECEBIDO" });
    await Promise.resolve(); await vi.advanceTimersByTimeAsync(45000); await falha;
    expect(e.parar).toHaveBeenCalledTimes(1);
  });
  it("encerra o receptor se o Worker falhar", async () => {
    const e = equipamento(); const recebido = escutarToken(e.fabrica, new AbortController().signal, vi.fn());
    e.erro(new Error("Worker desligado"));
    await expect(recebido).rejects.toMatchObject({ codigo: "FALHA_AUDIO" });
    expect(e.parar).toHaveBeenCalledTimes(1);
  });
  it.each(["NotAllowedError", "NotFoundError", "NotReadableError", "RangeError"])("traduz a falha %s", (nome) => {
    const erro = new Error(); erro.name = nome;
    expect(mensagemDoMicrofone(erro).codigo).not.toBe("FALHA_AUDIO");
  });
});

describe("cópia do modem entregue pela equipe", () => {
  it.each(Object.entries(origem.sha256))("preserva %s byte a byte no app isolado", (arquivo, esperado) => {
    const dados = readFileSync(new URL(`../../../public/audio/${arquivo}`, import.meta.url));
    expect(createHash("sha256").update(dados).digest("hex")).toBe(esperado);
  });
});
