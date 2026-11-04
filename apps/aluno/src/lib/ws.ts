// Cliente do servidor de sessão (docs/protocolo-v1.md): autentica com o JWT, casa respostas
// pelo ref, mantém a conexão viva com ping e reconecta com espera crescente.

import {
  Fechamento,
  INTERVALO_PING_MS,
  VERSAO_PROTOCOLO,
  mensagemDoErro,
  type Envelope,
  type Erro,
} from "./protocolo";

/** O pedaço do WebSocket que o cliente usa; nos testes, um socket falso. */
export interface SocketSessao {
  readyState: number;
  send(texto: string): void;
  close(codigo?: number, motivo?: string): void;
  onopen: (() => void) | null;
  onmessage: ((evento: { data: string }) => void) | null;
  onclose: ((evento: { code: number }) => void) | null;
}

export type EstadoConexao =
  | "conectando"
  | "pronto"
  | "reconectando"
  /** Token ausente, recusado ou expirado: é preciso entrar de novo. */
  | "sessao-expirada"
  /** O servidor não aceita esta versão do protocolo: é preciso recarregar o app. */
  | "incompativel"
  | "fechado";

/** Falha de uma requisição, com a mensagem pronta para exibir. */
export class ErroSessao extends Error {
  constructor(
    readonly codigo: string,
    message: string,
  ) {
    super(message);
    this.name = "ErroSessao";
  }
}

export interface OpcoesCliente {
  /** Endereço do /ws, ou uma função lida a cada conexão (para não depender de `location` no servidor). */
  url: string | (() => string);
  /** Lido a cada conexão, para usar o JWT mais recente. */
  token: () => string | null;
  criarSocket?: (url: string) => SocketSessao;
  tempoLimiteMs?: number;
}

/**
 * Endereço do servidor de sessão. Em produção o Nginx atende /ws na mesma origem da página; em
 * `npm run dev` o Next não repassa o WebSocket, então o padrão é o Docker Compose na porta 8088.
 */
export function urlDaSessao(
  local: { protocol: string; host: string } = globalThis.location,
  {
    desenvolvimento = process.env.NODE_ENV === "development",
    configurada = process.env.NEXT_PUBLIC_WS_URL,
  } = {},
): string {
  if (configurada) return configurada;
  if (desenvolvimento) return "ws://localhost:8088/ws";
  return `${local.protocol === "https:" ? "wss" : "ws"}://${local.host}/ws`;
}

const ABERTO = 1;
const ESPERA_INICIAL_MS = 1_000;
const ESPERA_MAXIMA_MS = 30_000;

interface Pendente {
  tipoResposta: string;
  resolver: (dados: unknown) => void;
  rejeitar: (erro: ErroSessao) => void;
  tempo: ReturnType<typeof setTimeout>;
}

export class ClienteSessao {
  private socket: SocketSessao | null = null;
  private estadoAtual: EstadoConexao = "fechado";
  private encerrado = false;
  private tentativas = 0;
  private contador = 0;
  private idAuth: string | null = null;
  private ping: ReturnType<typeof setInterval> | null = null;
  private reconexao: ReturnType<typeof setTimeout> | null = null;
  private readonly pendentes = new Map<string, Pendente>();
  private fila: Envelope[] = [];
  private readonly ouvintesEstado = new Set<(estado: EstadoConexao) => void>();
  private readonly ouvintesEvento = new Map<
    string,
    Set<(dados: unknown) => void>
  >();
  private diferenca = 0;

  constructor(private readonly opcoes: OpcoesCliente) {}

  get estado(): EstadoConexao {
    return this.estadoAtual;
  }

  /** Relógio do servidor menos o do navegador, pelo `ts` da última mensagem recebida. */
  get diferencaRelogioMs(): number {
    return this.diferenca;
  }

  conectar(): void {
    this.encerrado = false;
    this.abrirSocket();
  }

  fechar(): void {
    this.encerrado = true;
    this.limparTimers();
    this.socket?.close(Fechamento.NORMAL);
    this.socket = null;
    this.falharPendentes();
    this.mudarEstado("fechado");
  }

  /** Envia uma requisição; espera a conexão ficar pronta, se preciso. */
  requisitar<T>(tipo: string, dados: unknown): Promise<T> {
    const envelope: Envelope = {
      v: VERSAO_PROTOCOLO,
      tipo,
      id: this.novoId(),
      ts: Date.now(),
      dados,
    };
    return new Promise<T>((resolver, rejeitar) => {
      const tempo = setTimeout(() => {
        this.pendentes.delete(envelope.id!);
        this.fila = this.fila.filter((e) => e.id !== envelope.id);
        rejeitar(
          new ErroSessao(
            "TEMPO_ESGOTADO",
            "O servidor demorou para confirmar a presença. Confira seu histórico antes de tentar novamente.",
          ),
        );
      }, this.opcoes.tempoLimiteMs ?? 10_000);
      this.pendentes.set(envelope.id!, {
        tipoResposta: `${tipo}.ok`,
        resolver: resolver as (dados: unknown) => void,
        rejeitar,
        tempo,
      });
      if (this.estadoAtual === "pronto") this.enviar(envelope);
      else this.fila.push(envelope);
    });
  }

  aoEvento<T>(tipo: string, ouvinte: (dados: T) => void): () => void {
    const ouvintes = this.ouvintesEvento.get(tipo) ?? new Set();
    ouvintes.add(ouvinte as (dados: unknown) => void);
    this.ouvintesEvento.set(tipo, ouvintes);
    return () => ouvintes.delete(ouvinte as (dados: unknown) => void);
  }

  aoMudarEstado(ouvinte: (estado: EstadoConexao) => void): () => void {
    this.ouvintesEstado.add(ouvinte);
    return () => this.ouvintesEstado.delete(ouvinte);
  }

  private abrirSocket(): void {
    const token = this.opcoes.token();
    if (!token) {
      this.mudarEstado("sessao-expirada");
      return;
    }
    if (this.estadoAtual !== "reconectando") this.mudarEstado("conectando");

    const criar =
      this.opcoes.criarSocket ??
      ((url: string) => new WebSocket(url) as unknown as SocketSessao);
    let socket: SocketSessao;
    try {
      socket = criar(
        typeof this.opcoes.url === "function"
          ? this.opcoes.url()
          : this.opcoes.url,
      );
    } catch {
      this.aoFechar(1006);
      return;
    }
    this.socket = socket;
    socket.onopen = () => {
      if (this.socket !== socket) return;
      // O token não vai na URL, para não aparecer em logs (seção 1)
      this.idAuth = this.novoId();
      this.enviar({
        v: VERSAO_PROTOCOLO,
        tipo: "auth",
        id: this.idAuth,
        ts: Date.now(),
        dados: { token },
      });
    };
    socket.onmessage = (evento) => {
      if (this.socket === socket) this.receber(evento.data);
    };
    socket.onclose = (evento) => {
      if (this.socket !== socket) return;
      this.aoFechar(evento.code);
    };
  }

  private receber(texto: string): void {
    let envelope: Envelope;
    try {
      envelope = JSON.parse(texto) as Envelope;
    } catch {
      return;
    }
    if (!envelope || typeof envelope !== "object") return;
    if (typeof envelope.ts === "number")
      this.diferenca = envelope.ts - Date.now();
    if (envelope.v !== VERSAO_PROTOCOLO) return;

    if (envelope.ref && envelope.ref === this.idAuth) {
      if (envelope.tipo === "auth.ok") this.aoAutenticar();
      return;
    }
    if (envelope.ref) {
      const pendente = this.pendentes.get(envelope.ref);
      if (!pendente) return;
      clearTimeout(pendente.tempo);
      this.pendentes.delete(envelope.ref);
      if (envelope.tipo === "erro") {
        const erro = envelope.dados as Erro;
        pendente.rejeitar(new ErroSessao(erro.codigo, mensagemDoErro(erro)));
      } else if (envelope.tipo === pendente.tipoResposta) {
        pendente.resolver(envelope.dados);
      } else {
        pendente.rejeitar(
          new ErroSessao(
            "RESPOSTA_INVALIDA",
            "Não foi possível confirmar a resposta do servidor. Consulte seu histórico antes de tentar novamente.",
          ),
        );
      }
      return;
    }
    this.ouvintesEvento
      .get(envelope.tipo)
      ?.forEach((ouvinte) => ouvinte(envelope.dados));
  }

  private aoAutenticar(): void {
    this.tentativas = 0;
    this.mudarEstado("pronto");
    this.ping = setInterval(() => {
      this.enviar({
        v: VERSAO_PROTOCOLO,
        tipo: "ping",
        id: this.novoId(),
        ts: Date.now(),
        dados: {},
      });
    }, INTERVALO_PING_MS);
    const fila = this.fila;
    this.fila = [];
    fila.forEach((envelope) => this.enviar(envelope));
  }

  private aoFechar(codigo: number): void {
    this.socket = null;
    this.limparTimers();
    this.falharPendentes();
    if (this.encerrado) {
      this.mudarEstado("fechado");
    } else if (
      codigo === Fechamento.AUTENTICACAO_FALHOU ||
      codigo === Fechamento.TOKEN_EXPIROU
    ) {
      this.mudarEstado("sessao-expirada");
    } else if (codigo === Fechamento.VERSAO_NAO_SUPORTADA) {
      this.mudarEstado("incompativel");
    } else {
      // 1001 (servidor reiniciando), queda de rede e demais: tenta de novo (seção 8)
      this.mudarEstado("reconectando");
      const espera = Math.min(
        ESPERA_INICIAL_MS * 2 ** this.tentativas,
        ESPERA_MAXIMA_MS,
      );
      this.tentativas++;
      this.reconexao = setTimeout(() => this.abrirSocket(), espera);
    }
  }

  private falharPendentes(): void {
    const erro = new ErroSessao(
      "SEM_CONEXAO",
      "Sem conexão com o servidor de chamada. Tente de novo em instantes.",
    );
    this.pendentes.forEach((pendente) => {
      clearTimeout(pendente.tempo);
      pendente.rejeitar(erro);
    });
    this.pendentes.clear();
    this.fila = [];
  }

  private enviar(envelope: Envelope): void {
    if (this.socket?.readyState === ABERTO)
      this.socket.send(JSON.stringify(envelope));
  }

  private limparTimers(): void {
    if (this.ping) clearInterval(this.ping);
    if (this.reconexao) clearTimeout(this.reconexao);
    this.ping = null;
    this.reconexao = null;
  }

  private novoId(): string {
    this.contador++;
    return `r${this.contador}`;
  }

  private mudarEstado(estado: EstadoConexao): void {
    if (estado === this.estadoAtual) return;
    this.estadoAtual = estado;
    this.ouvintesEstado.forEach((ouvinte) => ouvinte(estado));
  }
}
