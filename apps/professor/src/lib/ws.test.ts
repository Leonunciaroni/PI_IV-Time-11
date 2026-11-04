import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Envelope } from "./protocolo";
import { ClienteSessao, ErroSessao, type EstadoConexao, type SocketSessao } from "./ws";

/** WebSocket falso: guarda o que o cliente envia e deixa o teste abrir, responder e derrubar. */
class SocketFalso implements SocketSessao {
  static criados: SocketFalso[] = [];
  readyState = 0;
  enviadas: Envelope[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((evento: { data: string }) => void) | null = null;
  onclose: ((evento: { code: number }) => void) | null = null;

  constructor(readonly url: string) {
    SocketFalso.criados.push(this);
  }
  send(texto: string) {
    this.enviadas.push(JSON.parse(texto));
  }
  close(codigo = 1000) {
    this.cair(codigo);
  }
  abrir() {
    this.readyState = 1;
    this.onopen?.();
  }
  receber(envelope: Partial<Envelope>) {
    this.onmessage?.({ data: JSON.stringify({ v: 1, ts: Date.now(), dados: {}, ...envelope }) });
  }
  responder(pedido: Envelope, tipo: string, dados: unknown) {
    this.receber({ tipo, ref: pedido.id, dados });
  }
  cair(codigo: number) {
    if (this.readyState === 3) return;
    this.readyState = 3;
    this.onclose?.({ code: codigo });
  }
  ultima(): Envelope {
    return this.enviadas[this.enviadas.length - 1];
  }
}

function novoCliente(token: string | null = "jwt-do-professor") {
  return new ClienteSessao({
    url: "ws://localhost:8088/ws",
    token: () => token,
    criarSocket: (url) => new SocketFalso(url),
  });
}

/** Conecta e autentica; devolve o cliente pronto e o socket atual. */
function clientePronto() {
  const cliente = novoCliente();
  cliente.conectar();
  const socket = SocketFalso.criados.at(-1)!;
  socket.abrir();
  socket.responder(socket.enviadas[0], "auth.ok", { usuarioId: "p1", nome: "Prof", papel: "PROFESSOR", expiraEm: 0 });
  return { cliente, socket };
}

beforeEach(() => {
  SocketFalso.criados = [];
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-03T19:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("ClienteSessao", () => {
  it("envia auth com o token assim que conecta e fica pronto com auth.ok", () => {
    const cliente = novoCliente();
    const estados: EstadoConexao[] = [];
    cliente.aoMudarEstado((estado) => estados.push(estado));
    cliente.conectar();
    const socket = SocketFalso.criados[0];
    expect(socket.url).toBe("ws://localhost:8088/ws");

    socket.abrir();
    expect(socket.enviadas[0]).toMatchObject({ v: 1, tipo: "auth", dados: { token: "jwt-do-professor" } });
    expect(cliente.estado).toBe("conectando");

    socket.responder(socket.enviadas[0], "auth.ok", { usuarioId: "p1", nome: "Prof", papel: "PROFESSOR", expiraEm: 0 });
    expect(cliente.estado).toBe("pronto");
    expect(estados).toEqual(["conectando", "pronto"]);
  });

  it("sem token, não conecta e marca a sessão como expirada", () => {
    const cliente = novoCliente(null);
    cliente.conectar();
    expect(SocketFalso.criados).toHaveLength(0);
    expect(cliente.estado).toBe("sessao-expirada");
  });

  it("envia a requisição no envelope e resolve com os dados da resposta de mesmo ref", async () => {
    const { cliente, socket } = clientePronto();
    const resposta = cliente.requisitar("sessao.abrir", { turmaId: "t1", modo: "CLASSICA" });

    const pedido = socket.ultima();
    expect(pedido).toMatchObject({ v: 1, tipo: "sessao.abrir", dados: { turmaId: "t1", modo: "CLASSICA" } });
    expect(pedido.id).toBeTruthy();
    expect(pedido.ts).toBe(Date.now());

    socket.receber({ tipo: "sessao.aberta", ref: "outro-id", dados: { sessaoId: "errada" } });
    socket.responder(pedido, "sessao.aberta", { sessaoId: "s1" });
    await expect(resposta).resolves.toEqual({ sessaoId: "s1" });
  });

  it("rejeita com o código e a mensagem quando a resposta é erro", async () => {
    const { cliente, socket } = clientePronto();
    const resposta = cliente.requisitar("sessao.abrir", { turmaId: "t1", modo: "CLASSICA" });
    socket.responder(socket.ultima(), "erro", { codigo: "SESSAO_JA_ABERTA", mensagem: "Já existe uma sessão aberta para a turma" });

    const erro = await resposta.catch((e: unknown) => e);
    expect(erro).toBeInstanceOf(ErroSessao);
    expect(erro).toMatchObject({ codigo: "SESSAO_JA_ABERTA", message: "Já existe uma chamada aberta para esta turma." });
  });

  it("guarda a requisição feita durante a conexão e envia quando fica pronto", async () => {
    const cliente = novoCliente();
    cliente.conectar();
    const resposta = cliente.requisitar("sessao.encerrar", { sessaoId: "s1" });
    const socket = SocketFalso.criados[0];
    socket.abrir();
    expect(socket.enviadas.map((e) => e.tipo)).toEqual(["auth"]);

    socket.responder(socket.enviadas[0], "auth.ok", {});
    expect(socket.ultima().tipo).toBe("sessao.encerrar");
    socket.responder(socket.ultima(), "sessao.encerrada", { totalPresentes: 3 });
    await expect(resposta).resolves.toEqual({ totalPresentes: 3 });
  });

  it("rejeita por tempo esgotado quando o servidor não responde em 10 s", async () => {
    const { cliente } = clientePronto();
    const resposta = cliente.requisitar("sessao.abrir", {});
    vi.advanceTimersByTime(10_000);
    await expect(resposta).rejects.toMatchObject({ codigo: "TEMPO_ESGOTADO" });
  });

  it("envia ping a cada 25 s enquanto está conectado", () => {
    const { socket } = clientePronto();
    vi.advanceTimersByTime(24_999);
    expect(socket.enviadas.filter((e) => e.tipo === "ping")).toHaveLength(0);
    vi.advanceTimersByTime(1);
    expect(socket.ultima()).toMatchObject({ tipo: "ping", dados: {} });
    vi.advanceTimersByTime(25_000);
    expect(socket.enviadas.filter((e) => e.tipo === "ping")).toHaveLength(2);
  });

  it("rejeita as requisições pendentes quando a conexão cai", async () => {
    const { cliente, socket } = clientePronto();
    const resposta = cliente.requisitar("presenca.marcar", {});
    socket.cair(1006);
    await expect(resposta).rejects.toMatchObject({ codigo: "SEM_CONEXAO" });
  });

  it("reconecta com espera de 1, 2, 4 s até 30 s e autentica de novo", () => {
    const { cliente, socket } = clientePronto();
    socket.cair(1001);
    expect(cliente.estado).toBe("reconectando");

    const esperas = [1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000];
    for (const espera of esperas) {
      const antes = SocketFalso.criados.length;
      vi.advanceTimersByTime(espera - 1);
      expect(SocketFalso.criados).toHaveLength(antes);
      vi.advanceTimersByTime(1);
      expect(SocketFalso.criados).toHaveLength(antes + 1);
      SocketFalso.criados.at(-1)!.cair(1006);
    }

    vi.advanceTimersByTime(30_000);
    const novo = SocketFalso.criados.at(-1)!;
    novo.abrir();
    expect(novo.enviadas[0]).toMatchObject({ tipo: "auth", dados: { token: "jwt-do-professor" } });
    novo.responder(novo.enviadas[0], "auth.ok", {});
    expect(cliente.estado).toBe("pronto");

    // Depois de reconectar, a espera volta a começar em 1 s
    novo.cair(1006);
    const antes = SocketFalso.criados.length;
    vi.advanceTimersByTime(1_000);
    expect(SocketFalso.criados).toHaveLength(antes + 1);
  });

  it.each([4001, 4003])("não reconecta e marca a sessão como expirada ao fechar com %i", (codigo) => {
    const { cliente, socket } = clientePronto();
    socket.cair(codigo);
    vi.advanceTimersByTime(60_000);
    expect(cliente.estado).toBe("sessao-expirada");
    expect(SocketFalso.criados).toHaveLength(1);
  });

  it("não reconecta quando a versão do protocolo não é aceita (4002)", () => {
    const { cliente, socket } = clientePronto();
    socket.cair(4002);
    vi.advanceTimersByTime(60_000);
    expect(cliente.estado).toBe("incompativel");
    expect(SocketFalso.criados).toHaveLength(1);
  });

  it("fechar encerra a conexão sem reconectar e para o ping", () => {
    const { cliente, socket } = clientePronto();
    cliente.fechar();
    vi.advanceTimersByTime(60_000);
    expect(cliente.estado).toBe("fechado");
    expect(SocketFalso.criados).toHaveLength(1);
    expect(socket.enviadas.filter((e) => e.tipo === "ping")).toHaveLength(0);
  });

  it("entrega eventos sem ref aos ouvintes do tipo, até cancelar a inscrição", () => {
    const { cliente, socket } = clientePronto();
    const recebidos: unknown[] = [];
    const cancelar = cliente.aoEvento("sessao.encerrada", (dados) => recebidos.push(dados));

    socket.receber({ tipo: "sessao.encerrada", dados: { sessaoId: "s1", motivo: "TEMPO_LIMITE" } });
    socket.receber({ tipo: "presenca.atualizada", dados: { sessaoId: "s1" } });
    cancelar();
    socket.receber({ tipo: "sessao.encerrada", dados: { sessaoId: "s2" } });

    expect(recebidos).toEqual([{ sessaoId: "s1", motivo: "TEMPO_LIMITE" }]);
  });

  it("mede a diferença entre o relógio do servidor e o do navegador pelo ts das mensagens", () => {
    const { cliente, socket } = clientePronto();
    socket.receber({ tipo: "pong", ref: "x", ts: Date.now() + 2_500 });
    expect(cliente.diferencaRelogioMs).toBe(2_500);
  });
});
