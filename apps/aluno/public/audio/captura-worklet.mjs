// Guilherme Preventi Correia
// RA: 25001694

// Apenas transporta PCM. O DSP fica em Worker, fora da thread de áudio e da interface.
class CapturaFsk extends AudioWorkletProcessor {
  constructor() {
    super();
    this.amostras = new Float32Array(2048);
    this.posicao = 0;
  }

  process(entradas, saidas) {
    // Saída silenciosa: não reproduz o microfone nos alto-falantes.
    for (const saida of saidas) for (const canal of saida) canal.fill(0);
    const canal = entradas[0]?.[0];
    if (canal) for (const amostra of canal) {
      this.amostras[this.posicao++] = amostra;
      if (this.posicao === this.amostras.length) {
        this.port.postMessage(this.amostras, [this.amostras.buffer]);
        this.amostras = new Float32Array(2048);
        this.posicao = 0;
      }
    }
    return true;
  }
}

registerProcessor('captura-fsk', CapturaFsk);
