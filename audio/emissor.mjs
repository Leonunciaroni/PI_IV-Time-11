// Independente das telas e do enquadramento FSK: codificar(token, contexto)
// deve retornar um AudioBuffer com um quadro completo do modem.
export const VOLUME_REFERENCIA = 0.3;
export const RAMPA_SEGUNDOS = 0.05;

export class EmissorToken {
  constructor({ contexto, gerarToken, codificar, periodoSegundos, t0 = 0,
    agora = Date.now, volume = VOLUME_REFERENCIA, aoErro = () => {} }) {
    if (!Number.isFinite(periodoSegundos) || periodoSegundos <= 0 || !Number.isFinite(t0)) {
      throw new RangeError("Janela TOTP inválida.");
    }
    if (!Number.isFinite(volume) || volume < 0 || volume > 1) {
      throw new RangeError("Volume deve estar entre 0 e 1.");
    }
    Object.assign(this, { contexto, gerarToken, codificar, periodoSegundos, t0, agora, volume, aoErro });
    this.ativo = false;
    this.fontes = new Set();
  }

  // Invocar a partir do gesto do usuário. O contexto pertence ao chamador.
  async iniciar() {
    if (this.ativo) return;
    this.ativo = true;
    const execucao = this.execucao = {};
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

  async emitir(execucao) {
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
    // Não transmite quadros cortados nem tokens de uma janela que já passou.
    if (!Number.isFinite(buffer.duration) || buffer.duration <= 2 * RAMPA_SEGUNDOS
      || buffer.duration >= this.periodoSegundos) {
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
    // Recalcula pelo relógio corrigido a cada janela; atrasos não se acumulam.
    this.temporizador = setTimeout(() => {
      this.emitir(execucao).catch((erro) => {
        if (this.execucao === execucao) {
          this.parar();
          this.aoErro(erro);
        }
      });
    }, Math.max(0, limite - this.agora()));
  }

  parar() {
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
