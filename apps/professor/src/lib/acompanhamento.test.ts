/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { AcompanhamentoSessao, type ClienteAcompanhamento } from "./acompanhamento";
import type { Envelope, PresencaAtualizada, SessaoEstado, SessaoModoAlterado } from "./protocolo";
import { ClienteSessao, ErroSessao, type EstadoConexao, type SocketSessao } from "./ws";

interface Pedido {
  tipo: string;
  dados: unknown;
  resolver: (dados: unknown) => void;
  rejeitar: (erro: unknown) => void;
}

/** Cliente simulado: o teste muda o estado da conexão, emite eventos e responde os pedidos. */
class ClienteFalso implements ClienteAcompanhamento {
  estado: EstadoConexao = "conectando";
  pedidos: Pedido[] = [];
  private readonly ouvintesEstado = new Set<(estado: EstadoConexao) => void>();
  private readonly ouvintesEvento = new Map<string, Set<(dados: unknown) => void>>();

  aoMudarEstado(ouvinte: (estado: EstadoConexao) => void): () => void {
    this.ouvintesEstado.add(ouvinte);
    return () => this.ouvintesEstado.delete(ouvinte);
  }

  aoEvento<T>(tipo: string, ouvinte: (dados: T) => void): () => void {
    const ouvintes = this.ouvintesEvento.get(tipo) ?? new Set();
    ouvintes.add(ouvinte as (dados: unknown) => void);
    this.ouvintesEvento.set(tipo, ouvintes);
    return () => ouvintes.delete(ouvinte as (dados: unknown) => void);
  }

  requisitar<T>(tipo: string, dados: unknown): Promise<T> {
    return new Promise<T>((resolver, rejeitar) => {
      this.pedidos.push({ tipo, dados, resolver: resolver as (dados: unknown) => void, rejeitar });
    });
  }

  mudarEstado(estado: EstadoConexao) {
    this.estado = estado;
    this.ouvintesEstado.forEach((ouvinte) => ouvinte(estado));
  }

  emitir(tipo: string, dados: unknown) {
    this.ouvintesEvento.get(tipo)?.forEach((ouvinte) => ouvinte(dados));
  }

  ultimo(): Pedido {
    return this.pedidos[this.pedidos.length - 1];
  }
}

function estado(alunos: [string, string, number][], sessaoId = "s1"): SessaoEstado {
  return {
    sessaoId,
    modo: "QRCODE",
    abertaEm: 1_000,
    presentes: alunos.map(([alunoId, nome, registradoEm]) => ({ alunoId, nome, origem: "QRCODE", registradoEm })),
    totalMatriculados: 30,
  };
}

function evento(alunoId: string, registradoEm: number, { presente = true, sessaoId = "s1", totalPresentes = 1 } = {}): PresencaAtualizada {
  return {
    sessaoId,
    alunoId,
    nomeAluno: `Aluno ${alunoId}`,
    presente,
    origem: "QRCODE",
    registradoEm,
    totalPresentes,
    totalMatriculados: 30,
  };
}

function troca(modo: SessaoModoAlterado["modo"], salaId: string | null): SessaoModoAlterado {
  return { sessaoId: "s1", modo, salaId, alteradoEm: 2_000 };
}

/** Deixa as promessas resolvidas rodarem seus then. */
const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

function iniciado(estadoInicial: EstadoConexao = "conectando") {
  const cliente = new ClienteFalso();
  cliente.estado = estadoInicial;
  const acompanhamento = new AcompanhamentoSessao(cliente, "s1");
  const parar = acompanhamento.iniciar();
  return { cliente, acompanhamento, parar };
}

describe("AcompanhamentoSessao", () => {
  it("pede sessao.acompanhar quando a conexão fica pronta e mostra o estado", async () => {
    const { cliente, acompanhamento } = iniciado();
    expect(cliente.pedidos).toHaveLength(0);
    expect(acompanhamento.situacao).toMatchObject({ carregado: false, presentes: [], totalMatriculados: null });

    cliente.mudarEstado("pronto");
    expect(cliente.pedidos).toHaveLength(1);
    expect(cliente.ultimo()).toMatchObject({ tipo: "sessao.acompanhar", dados: { sessaoId: "s1" } });

    cliente.ultimo().resolver(estado([["a1", "Ana Souza", 100]]));
    await esperar();
    expect(acompanhamento.situacao).toMatchObject({
      carregado: true,
      totalPresentes: 1,
      totalMatriculados: 30,
      presentes: [{ alunoId: "a1", nome: "Ana Souza", origem: "QRCODE", registradoEm: 100 }],
      encerrada: null,
      erro: null,
    });
  });

  it("pede na hora se a conexão já estava pronta", () => {
    const { cliente } = iniciado("pronto");
    expect(cliente.pedidos).toHaveLength(1);
  });

  it("evento antes da resposta aparece na hora e não some nem duplica com o estado", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.emitir("presenca.atualizada", evento("a2", 200));
    expect(acompanhamento.situacao.presentes.map((p) => p.alunoId)).toEqual(["a2"]);

    cliente.ultimo().resolver(estado([["a1", "Ana", 100], ["a2", "Aluno a2", 200]]));
    await esperar();
    expect(acompanhamento.situacao.presentes.map((p) => p.alunoId)).toEqual(["a2", "a1"]);
  });

  it("ignora eventos e respostas de outra sessão", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.emitir("presenca.atualizada", evento("a9", 200, { sessaoId: "outra" }));
    cliente.ultimo().resolver(estado([["a9", "Intruso", 100]], "outra"));
    await esperar();
    expect(acompanhamento.situacao).toMatchObject({ carregado: false, presentes: [] });
  });

  it("na reconexão pede de novo e o estado novo substitui o anterior", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.ultimo().resolver(estado([["a1", "Ana", 100]]));
    await esperar();

    cliente.mudarEstado("reconectando");
    cliente.mudarEstado("pronto");
    expect(cliente.pedidos).toHaveLength(2);
    // Enquanto o estado novo não chega, a lista anterior continua na tela
    expect(acompanhamento.situacao.presentes.map((p) => p.alunoId)).toEqual(["a1"]);

    cliente.ultimo().resolver(estado([["a2", "Bruno", 300]]));
    await esperar();
    expect(acompanhamento.situacao.presentes.map((p) => p.alunoId)).toEqual(["a2"]);
  });

  it("resposta de uma conexão que caiu não sobrescreve o estado atual", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    const antigo = cliente.ultimo();
    cliente.mudarEstado("reconectando");
    cliente.mudarEstado("pronto");
    const novo = cliente.ultimo();

    novo.resolver(estado([["a2", "Bruno", 300]]));
    await esperar();
    antigo.resolver(estado([["a1", "Ana", 100]]));
    antigo.rejeitar(new ErroSessao("SEM_CONEXAO", "Sem conexão"));
    await esperar();
    expect(acompanhamento.situacao.presentes.map((p) => p.alunoId)).toEqual(["a2"]);
    expect(acompanhamento.situacao.erro).toBeNull();
  });

  it.each([
    ["SESSAO_ENCERRADA", "Esta chamada já foi encerrada."],
    ["SESSAO_NAO_ENCONTRADA", "Esta chamada não existe mais."],
  ])("%s vira o sinal de encerrada com a mensagem em português", async (codigo, mensagem) => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.ultimo().rejeitar(new ErroSessao(codigo, mensagem));
    await esperar();
    expect(acompanhamento.situacao).toMatchObject({ encerrada: mensagem, erro: null });
  });

  it("outros erros viram mensagem de erro, que some quando o estado chega", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.ultimo().rejeitar(new ErroSessao("PERMISSAO_NEGADA", "Você não tem permissão para esta chamada."));
    await esperar();
    expect(acompanhamento.situacao).toMatchObject({ encerrada: null, erro: "Você não tem permissão para esta chamada." });

    void acompanhamento.recarregar();
    cliente.ultimo().resolver(estado([]));
    await esperar();
    expect(acompanhamento.situacao).toMatchObject({ erro: null, carregado: true });
  });

  it("recarregar resolve depois que o estado novo foi incorporado", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.ultimo().resolver(estado([]));
    await esperar();

    const recarga = acompanhamento.recarregar();
    expect(cliente.pedidos).toHaveLength(2);
    cliente.ultimo().resolver(estado([["a1", "Ana", 100], ["a2", "Bruno", 101]]));
    await recarga;
    expect(acompanhamento.situacao.totalPresentes).toBe(2);
  });

  it("presenca.marcar respondida entra pela mesma junção, sem duplicar com o evento", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.ultimo().resolver(estado([]));
    await esperar();
    acompanhamento.registrar(evento("a1", 100));
    cliente.emitir("presenca.atualizada", evento("a1", 100));
    expect(acompanhamento.situacao.presentes).toHaveLength(1);
  });

  it("avisa os inscritos e mantém o mesmo objeto enquanto nada muda", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    const avisos = vi.fn();
    acompanhamento.inscrever(avisos);
    const antes = acompanhamento.situacao;
    expect(acompanhamento.situacao).toBe(antes);

    cliente.emitir("presenca.atualizada", evento("a1", 100));
    expect(avisos).toHaveBeenCalledTimes(1);
    expect(acompanhamento.situacao).not.toBe(antes);
  });

  it("o modo vem do estado e muda com sessao.modo_alterado desta sessão", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    expect(acompanhamento.situacao).toMatchObject({ modo: null, salaId: undefined });

    cliente.ultimo().resolver(estado([]));
    await esperar();
    expect(acompanhamento.situacao).toMatchObject({ modo: "QRCODE", salaId: undefined });

    cliente.emitir("sessao.modo_alterado", troca("CLASSICA", null));
    expect(acompanhamento.situacao).toMatchObject({ modo: "CLASSICA", salaId: null });

    cliente.emitir("sessao.modo_alterado", { ...troca("SOM", null), sessaoId: "outra" });
    expect(acompanhamento.situacao.modo).toBe("CLASSICA");
  });

  it("troca chegada antes do estado vence o modo antigo que o estado ainda traz", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.emitir("sessao.modo_alterado", troca("SOM", null));

    // O servidor leu o estado antes de gravar a troca: ainda diz QRCODE
    cliente.ultimo().resolver(estado([]));
    await esperar();

    expect(acompanhamento.situacao.modo).toBe("SOM");
  });

  it("na reconexão o modo do estado novo vale, sem reaplicar uma troca de antes da queda", async () => {
    const { cliente, acompanhamento } = iniciado("pronto");
    cliente.ultimo().resolver(estado([]));
    await esperar();
    cliente.emitir("sessao.modo_alterado", troca("SOM", null));

    cliente.mudarEstado("reconectando");
    cliente.mudarEstado("pronto");
    // Com a conexão fora, outra aba voltou para a clássica: o estado novo diz isso
    cliente.ultimo().resolver({ ...estado([]), modo: "CLASSICA" });
    await esperar();

    expect(acompanhamento.situacao.modo).toBe("CLASSICA");
  });

  it("a resposta de sessao.trocar_modo entra por registrarTroca e avisa os inscritos", () => {
    const { acompanhamento } = iniciado("pronto");
    const avisos = vi.fn();
    acompanhamento.inscrever(avisos);

    acompanhamento.registrarTroca(troca("QRCODE", "sala-1"));

    expect(acompanhamento.situacao).toMatchObject({ modo: "QRCODE", salaId: "sala-1" });
    expect(avisos).toHaveBeenCalledTimes(1);
  });

  it("depois de parar, ignora eventos, respostas e reconexões", async () => {
    const { cliente, acompanhamento, parar } = iniciado("pronto");
    parar();
    cliente.emitir("presenca.atualizada", evento("a1", 100));
    cliente.ultimo().resolver(estado([["a2", "Bruno", 100]]));
    cliente.mudarEstado("reconectando");
    cliente.mudarEstado("pronto");
    await esperar();
    expect(cliente.pedidos).toHaveLength(1);
    expect(acompanhamento.situacao).toMatchObject({ carregado: false, presentes: [] });
  });
});

/** WebSocket falso mínimo, para conferir o acompanhamento com o ClienteSessao de verdade. */
class SocketFalso implements SocketSessao {
  static criados: SocketFalso[] = [];
  readyState = 0;
  enviadas: Envelope[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((evento: { data: string }) => void) | null = null;
  onclose: ((evento: { code: number }) => void) | null = null;

  constructor() {
    SocketFalso.criados.push(this);
  }
  send(texto: string) {
    this.enviadas.push(JSON.parse(texto));
  }
  close(codigo = 1000) {
    this.cair(codigo);
  }
  abrirEAutenticar() {
    this.readyState = 1;
    this.onopen?.();
    this.responder(this.enviadas[0], "auth.ok", { usuarioId: "p1", nome: "Prof", papel: "PROFESSOR", expiraEm: 0 });
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
  pedidos(tipo: string): Envelope[] {
    return this.enviadas.filter((envelope) => envelope.tipo === tipo);
  }
}

describe("AcompanhamentoSessao com o ClienteSessao", () => {
  afterEach(() => {
    vi.useRealTimers();
    SocketFalso.criados = [];
  });

  it("refaz o acompanhar depois que a conexão cai e volta, sem mostrar o erro da queda", async () => {
    vi.useFakeTimers();
    const cliente = new ClienteSessao({ url: "ws://teste/ws", token: () => "jwt", criarSocket: () => new SocketFalso() });
    const acompanhamento = new AcompanhamentoSessao(cliente, "s1");
    acompanhamento.iniciar();
    cliente.conectar();

    const primeiro = SocketFalso.criados[0];
    primeiro.abrirEAutenticar();
    expect(primeiro.pedidos("sessao.acompanhar")).toHaveLength(1);
    // Evento antes da resposta, como permite a seção 4.5
    primeiro.receber({ tipo: "presenca.atualizada", dados: evento("a1", 100) });
    primeiro.responder(primeiro.pedidos("sessao.acompanhar")[0], "sessao.estado", estado([["a1", "Aluno a1", 100]]));
    await vi.advanceTimersByTimeAsync(0);
    expect(acompanhamento.situacao).toMatchObject({ carregado: true, totalPresentes: 1 });

    // Cai com um acompanhar pendente: a rejeição SEM_CONEXAO não vira erro na tela
    void acompanhamento.recarregar();
    primeiro.cair(1006);
    await vi.advanceTimersByTimeAsync(0);
    expect(acompanhamento.situacao.erro).toBeNull();

    await vi.advanceTimersByTimeAsync(1_000);
    const segundo = SocketFalso.criados[1];
    segundo.abrirEAutenticar();
    const pedido = segundo.pedidos("sessao.acompanhar");
    expect(pedido).toHaveLength(1);
    expect(pedido[0].dados).toEqual({ sessaoId: "s1" });

    segundo.responder(pedido[0], "sessao.estado", estado([["a2", "Bruno", 300]]));
    await vi.advanceTimersByTimeAsync(0);
    expect(acompanhamento.situacao.presentes.map((p) => p.alunoId)).toEqual(["a2"]);
    cliente.fechar();
  });
});
