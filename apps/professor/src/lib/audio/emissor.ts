/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
// Emissão do token em loop (#46), portada para TypeScript de audio/emissor.mjs (develop, commit
// e95e449, PR #104) com a mesma lógica. A imagem Docker do professor só enxerga apps/professor,
// por isso a cópia. Se o módulo original mudar, atualize esta cópia.

export const VOLUME_REFERENCIA = 0.3;
export const RAMPA_SEGUNDOS = 0.05;

/** O pedaço do AudioContext que o emissor usa; nos testes, um contexto simulado. */
export interface ContextoDeEmissao {
  state: string;
  currentTime: number;
  destination: AudioNode;
  resume(): Promise<void>;
  createBufferSource(): AudioBufferSourceNode;
  createGain(): GainNode;
}

export interface OpcoesEmissor<C extends ContextoDeEmissao = ContextoDeEmissao> {
  contexto: C;
  gerarToken: (instanteMs: number) => string | Promise<string>;
  /** Devolve o quadro completo do modem (preâmbulo, token e verificação). */
  codificar: (token: string, contexto: C) => AudioBuffer | Promise<AudioBuffer>;
  periodoSegundos: number;
  t0?: number;
  /** Relógio do servidor, corrigido pela diferença medida no WebSocket. */
  agora?: () => number;
  volume?: number;
  aoErro?: (erro: unknown) => void;
}

interface FonteAtiva {
  fonte: AudioBufferSourceNode;
  ganho: GainNode;
}

export class EmissorToken<C extends ContextoDeEmissao = ContextoDeEmissao> {
  readonly contexto: C;
  private readonly gerarToken: OpcoesEmissor<C>["gerarToken"];
  private readonly codificar: OpcoesEmissor<C>["codificar"];
  private readonly periodoSegundos: number;
  private readonly t0: number;
  private readonly agora: () => number;
  private readonly aoErro: (erro: unknown) => void;
  /** Lido a cada quadro: mudar vale a partir do quadro seguinte. */
  volume: number;
  ativo = false;
  readonly fontes = new Set<FonteAtiva>();
  private execucao: object | null = null;
  private temporizador: ReturnType<typeof setTimeout> | undefined;

  constructor({
    contexto,
    gerarToken,
    codificar,
    periodoSegundos,
    t0 = 0,
    agora = Date.now,
    volume = VOLUME_REFERENCIA,
    aoErro = () => {},
  }: OpcoesEmissor<C>) {
    if (!Number.isFinite(periodoSegundos) || periodoSegundos <= 0 || !Number.isFinite(t0)) {
      throw new RangeError("Janela TOTP inválida.");
    }
    if (!Number.isFinite(volume) || volume < 0 || volume > 1) {
      throw new RangeError("Volume deve estar entre 0 e 1.");
    }
    this.contexto = contexto;
    this.gerarToken = gerarToken;
    this.codificar = codificar;
    this.periodoSegundos = periodoSegundos;
    this.t0 = t0;
    this.agora = agora;
    this.volume = volume;
    this.aoErro = aoErro;
  }

  /** Chamar a partir do gesto do usuário (o navegador só libera o áudio assim). */
  async iniciar(): Promise<void> {
    if (this.ativo) return;
    this.ativo = true;
    const execucao = (this.execucao = {});
    try {
      await this.contexto.resume();
      if (this.execucao === execucao) await this.emitir(execucao);
    } catch (erro) {
      if (this.execucao === execucao) {
        this.parar();
        this.aoErro(erro);
      }
      throw erro;
    }
  }

  private async emitir(execucao: object): Promise<void> {
    if (this.execucao !== execucao) return;
    const periodoMs = this.periodoSegundos * 1000;
    const instante = this.agora();
    const janela = Math.floor((instante - this.t0 * 1000) / periodoMs);
    const limite = this.t0 * 1000 + (janela + 1) * periodoMs;
    const token = await this.gerarToken(instante);
    if (this.execucao !== execucao) return;
    const buffer = await this.codificar(token, this.contexto);
    if (this.execucao !== execucao) return;
    const restante = (limite - this.agora()) / 1000;
    // Não transmite quadros cortados nem tokens de uma janela que já passou
    if (
      !Number.isFinite(buffer.duration) ||
      buffer.duration <= 2 * RAMPA_SEGUNDOS ||
      buffer.duration >= this.periodoSegundos
    ) {
      throw new RangeError("O quadro deve comportar as rampas e ser menor que a janela TOTP.");
    }
    if (restante >= buffer.duration && this.contexto.state === "running") {
      const fonte = this.contexto.createBufferSource();
      const ganho = this.contexto.createGain();
      const inicio = this.contexto.currentTime;
      const fim = inicio + buffer.duration;
      fonte.buffer = buffer;
      ganho.gain.setValueAtTime(0, inicio);
      ganho.gain.linearRampToValueAtTime(this.volume, inicio + RAMPA_SEGUNDOS);
      ganho.gain.setValueAtTime(this.volume, fim - RAMPA_SEGUNDOS);
      ganho.gain.linearRampToValueAtTime(0, fim);
      fonte.connect(ganho).connect(this.contexto.destination);
      const registro = { fonte, ganho };
      this.fontes.add(registro);
      fonte.onended = () => {
        fonte.disconnect();
        ganho.disconnect();
        this.fontes.delete(registro);
      };
      fonte.start(inicio);
      fonte.stop(fim);
    }
    // Recalcula pelo relógio corrigido a cada janela; atrasos não se acumulam
    this.temporizador = setTimeout(
      () => {
        this.emitir(execucao).catch((erro) => {
          if (this.execucao === execucao) {
            this.parar();
            this.aoErro(erro);
          }
        });
      },
      Math.max(0, limite - this.agora()),
    );
  }

  parar(): void {
    this.ativo = false;
    this.execucao = null;
    clearTimeout(this.temporizador);
    for (const { fonte, ganho } of this.fontes) {
      const t = this.contexto.currentTime;
      ganho.gain.cancelAndHoldAtTime(t);
      ganho.gain.linearRampToValueAtTime(0, t + RAMPA_SEGUNDOS);
      fonte.stop(t + RAMPA_SEGUNDOS);
    }
  }
}
