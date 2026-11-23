// Guilherme Preventi Correia
// RA: 25001694

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ClienteSessao, urlDaSessao, type SocketSessao } from "./ws";
import type { Envelope } from "./protocolo";

class SocketFalso implements SocketSessao {
  readyState = 0;
  enviadas: Envelope[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((evento: { data: string }) => void) | null = null;
  onclose: ((evento: { code: number }) => void) | null = null;
  send(texto: string) {
    this.enviadas.push(JSON.parse(texto));
  }
  close(code = 1000) {
    this.readyState = 3;
    this.onclose?.({ code });
  }
  abrir() {
    this.readyState = 1;
    this.onopen?.();
  }
  receber(tipo: string, dados: unknown, ref?: string) {
    this.onmessage?.({
      data: JSON.stringify({ v: 1, tipo, dados, ref, ts: Date.now() }),
    });
  }
}
const clientes: ClienteSessao[] = [];
beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  clientes.forEach((c) => c.fechar());
  clientes.length = 0;
  vi.useRealTimers();
});
function criar() {
  const sockets: SocketFalso[] = [];
  const cliente = new ClienteSessao({
    url: "ws://teste/ws",
    token: () => "jwt-do-aluno",
    criarSocket: () => {
      const socket = new SocketFalso();
      sockets.push(socket);
      return socket;
    },
  });
  clientes.push(cliente);
  cliente.conectar();
  return { cliente, sockets, socket: sockets[0] };
}
function pronto() {
  const teste = criar();
  teste.socket.abrir();
  teste.socket.receber(
    "auth.ok",
    { papel: "ALUNO" },
    teste.socket.enviadas[0].id,
  );
  return teste;
}
describe("transporte do aluno", () => {
  it("autentica pelo envelope antes do check-in, sem JWT na URL", async () => {
    const { cliente, socket } = criar();
    const pedido = cliente.requisitar("checkin", { sessaoId: "s1" });
    socket.abrir();
    expect(socket.enviadas.map((e) => e.tipo)).toEqual(["auth"]);
    expect(socket.enviadas[0].dados).toEqual({ token: "jwt-do-aluno" });
    socket.receber("auth.ok", {}, socket.enviadas[0].id);
    expect(socket.enviadas.map((e) => e.tipo)).toEqual(["auth", "checkin"]);
    socket.receber(
      "checkin.ok",
      { sessaoId: "s1", registradoEm: 123 },
      socket.enviadas[1].id,
    );
    await expect(pedido).resolves.toEqual({
      sessaoId: "s1",
      registradoEm: 123,
    });
  });
  it("só aceita checkin.ok correlacionado; ref alheio não confirma a presença", async () => {
    const { cliente, socket } = pronto();
    const pedido = cliente.requisitar("checkin", {});
    const resultado = expect(pedido).rejects.toMatchObject({
      codigo: "RESPOSTA_INVALIDA",
    });
    socket.receber("checkin.ok", {}, "ref-alheio");
    socket.receber("pong", {}, socket.enviadas.at(-1)!.id);
    await resultado;
  });
  it("traduz erros correlacionados, inclusive TOKEN_EXPIRADO", async () => {
    const { cliente, socket } = pronto();
    const pedido = cliente.requisitar("checkin", {});
    const resultado = expect(pedido).rejects.toThrow(/QR Code expirou/);
    socket.receber(
      "erro",
      { codigo: "TOKEN_EXPIRADO", mensagem: "erro técnico" },
      socket.enviadas.at(-1)!.id,
    );
    await resultado;
  });
  it("não reenvia check-in após reconectar e encerra o pedido pendente", async () => {
    const { cliente, socket, sockets } = pronto();
    const pedido = cliente.requisitar("checkin", {});
    const resultado = expect(pedido).rejects.toMatchObject({
      codigo: "SEM_CONEXAO",
    });
    socket.close(1001);
    await resultado;
    vi.advanceTimersByTime(1000);
    expect(sockets).toHaveLength(2);
    sockets[1].abrir();
    sockets[1].receber("auth.ok", {}, sockets[1].enviadas[0].id);
    expect(sockets[1].enviadas.map((e) => e.tipo)).toEqual(["auth"]);
  });
  it("avisa quando a resposta demora e mantém a conexão viva com ping", async () => {
    const { cliente, socket } = pronto();
    const pedido = cliente.requisitar("checkin", {});
    const resultado = expect(pedido).rejects.toMatchObject({
      codigo: "TEMPO_ESGOTADO",
    });
    vi.advanceTimersByTime(10000);
    await resultado;
    vi.advanceTimersByTime(15000);
    expect(socket.enviadas.at(-1)?.tipo).toBe("ping");
  });
  it.each([4001, 4003])(
    "não reconecta depois de autenticação recusada/expirada (%i)",
    (codigo) => {
      const { cliente, socket, sockets } = pronto();
      socket.close(codigo);
      vi.advanceTimersByTime(60000);
      expect(cliente.estado).toBe("sessao-expirada");
      expect(sockets).toHaveLength(1);
    },
  );
  it("usa wss em HTTPS e permite configurar o endereço em desenvolvimento", () => {
    expect(
      urlDaSessao(
        { protocol: "https:", host: "escola.test" },
        { desenvolvimento: false },
      ),
    ).toBe("wss://escola.test/ws");
    expect(
      urlDaSessao(
        { protocol: "http:", host: "localhost:3001" },
        { desenvolvimento: true },
      ),
    ).toBe("ws://localhost:8088/ws");
    expect(
      urlDaSessao(
        { protocol: "https:", host: "escola.test" },
        { configurada: "wss://ws.escola.test/ws" },
      ),
    ).toBe("wss://ws.escola.test/ws");
  });
});
