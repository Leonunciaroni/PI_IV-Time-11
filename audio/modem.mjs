// FSK v1: somente o token; sessaoId e validação TOTP continuam no protocolo da aplicação.
export const CONFIGURACAO_PADRAO = Object.freeze({
  frequencia0: 18500,
  frequencia1: 19500,
  duracaoSimboloMs: 20,
  amplitudeMinima: 0.0001,
  contrasteMinimo: 0.6,
});
export const MARGEM_SEGUNDOS = 0.06;
const CABECALHO = Object.freeze([0x55, 0x55, 0x55, 0x55, 0xd3, 0x91]);
const VERSAO = 1;
export const BYTES_QUADRO = 15;
const BITS_QUADRO = BYTES_QUADRO * 8;
const bitsDe = (bytes) => Array.from(bytes).flatMap((byte) => Array.from({ length: 8 }, (_, i) => (byte >> (7 - i)) & 1));
const BITS_CABECALHO = bitsDe(CABECALHO);

export function configurar(opcoes = {}, taxaAmostragem = 48000) {
  const config = { ...CONFIGURACAO_PADRAO, ...opcoes };
  for (const chave of Object.keys(CONFIGURACAO_PADRAO)) {
    if (!Number.isFinite(config[chave])) throw new RangeError(`Parâmetro inválido: ${chave}.`);
  }
  if (config.frequencia0 < 18000 || config.frequencia0 > 20000 || config.frequencia1 < 18000 || config.frequencia1 > 20000) {
    throw new RangeError('As duas frequências devem estar entre 18000 e 20000 Hz.');
  }
  if (config.duracaoSimboloMs < 10 || config.duracaoSimboloMs > 100) {
    throw new RangeError('A duração do símbolo deve estar entre 10 e 100 ms.');
  }
  if (Math.abs(config.frequencia1 - config.frequencia0) < 2000 / config.duracaoSimboloMs) {
    throw new RangeError('As frequências estão muito próximas para a duração do símbolo.');
  }
  if (config.amplitudeMinima <= 0 || config.amplitudeMinima > 1 || config.contrasteMinimo <= 0 || config.contrasteMinimo >= 1) {
    throw new RangeError('Limiar de amplitude ou contraste inválido.');
  }
  if (!Number.isInteger(taxaAmostragem) || taxaAmostragem <= 0 || taxaAmostragem > 384000
      || Math.max(config.frequencia0, config.frequencia1) + 100 >= taxaAmostragem / 2) {
    throw new RangeError('Taxa de amostragem insuficiente para a faixa escolhida (Nyquist).');
  }
  return { ...config, taxaAmostragem, amostrasSimbolo: Math.round(taxaAmostragem * config.duracaoSimboloMs / 1000) };
}

/** CRC-16/IBM-3740 (CCITT-FALSE): poly 0x1021, init 0xffff, sem reflexão ou xor final. */
export function crc16(bytes) {
  let crc = 0xffff;
  for (const byte of bytes) {
    crc ^= byte << 8;
    for (let i = 0; i < 8; i++) crc = ((crc << 1) ^ (crc & 0x8000 ? 0x1021 : 0)) & 0xffff;
  }
  return crc;
}

export function montarQuadro(token) {
  if (typeof token !== 'string' || !/^[0-9]{6}$/.test(token)) throw new TypeError('O token deve conter exatamente seis dígitos.');
  const bytes = new Uint8Array(BYTES_QUADRO);
  bytes.set(CABECALHO);
  bytes[6] = VERSAO;
  for (let i = 0; i < 6; i++) bytes[7 + i] = token.charCodeAt(i);
  const crc = crc16(bytes.subarray(6, 13));
  bytes[13] = crc >> 8;
  bytes[14] = crc & 255;
  return bytes;
}

export function lerQuadro(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.length !== BYTES_QUADRO) throw new Error('Tamanho de quadro inválido.');
  if (!CABECALHO.every((byte, i) => bytes[i] === byte) || bytes[6] !== VERSAO) throw new Error('Cabeçalho ou versão inválidos.');
  if (crc16(bytes.subarray(6, 13)) !== ((bytes[13] << 8) | bytes[14])) throw new Error('Checksum inválido.');
  const token = String.fromCharCode(...bytes.subarray(7, 13));
  if (!/^[0-9]{6}$/.test(token)) throw new Error('Conteúdo do token inválido.');
  return token;
}

export function codificarAmostras(token, taxaAmostragem = 48000, opcoes = {}) {
  const config = configurar(opcoes, taxaAmostragem);
  const bits = bitsDe(montarQuadro(token));
  const margem = Math.ceil(MARGEM_SEGUNDOS * taxaAmostragem);
  const tamanho = bits.length * config.amostrasSimbolo;
  const sinal = new Float32Array(margem * 2 + tamanho);
  const rampa = Math.round(taxaAmostragem * 0.005);
  let fase = 0;
  for (let i = 0; i < tamanho; i++) {
    const frequencia = bits[Math.floor(i / config.amostrasSimbolo)] ? config.frequencia1 : config.frequencia0;
    const envelope = Math.min(1, i / rampa, (tamanho - 1 - i) / rampa);
    sinal[margem + i] = Math.sin(fase) * envelope;
    // Fase contínua nas trocas de frequência; margens silenciosas preservam o fade do emissor.
    fase = (fase + 2 * Math.PI * frequencia / taxaAmostragem) % (2 * Math.PI);
  }
  return sinal;
}

/** Adaptador Web Audio compatível com EmissorToken (#46). Amplitude é controlada pelo emissor. */
export function criarModem(opcoes = {}) {
  return {
    codificar(token, contexto) {
      const sinal = codificarAmostras(token, contexto.sampleRate, opcoes);
      const buffer = contexto.createBuffer(1, sinal.length, contexto.sampleRate);
      buffer.getChannelData(0).set(sinal);
      return buffer;
    },
  };
}

/**
 * Decodificador incremental: busca preâmbulo + sincronismo em oito fases por símbolo,
 * mede os dois tons com Goertzel e só entrega tokens com versão/formato/CRC válidos.
 * O buffer circular tem tamanho fixo, independente da duração da captura.
 */
export class DecodificadorFsk {
  constructor(taxaAmostragem = 48000, opcoes = {}) {
    this.config = configurar(opcoes, taxaAmostragem);
    this.n = this.config.amostrasSimbolo;
    this.passo = Math.max(1, Math.round(this.n / 8));
    this.janela = Math.round(this.n * 0.6);
    this.deslocamento = Math.round(this.n * 0.2);
    this.coeficientes = [this.config.frequencia0, this.config.frequencia1].map((f) => 2 * Math.cos(2 * Math.PI * f / taxaAmostragem));
    this.buffer = new Float32Array((BITS_QUADRO + 8) * this.n);
    this.reiniciar();
  }

  reiniciar() {
    this.buffer.fill(0);
    this.total = 0;
    this.proximoInicio = 0;
    this.proximaAnalise = 0;
    this.candidato = null;
    this.descartados = 0;
  }

  processar(amostras) {
    if (!(amostras instanceof Float32Array)) throw new TypeError('Forneça amostras PCM Float32Array mono.');
    const tokens = [];
    for (const amostra of amostras) {
      this.buffer[this.total % this.buffer.length] = Number.isFinite(amostra) ? amostra : 0;
      this.total++;
      if (this.total >= this.proximaAnalise) {
        this.#avancar(tokens);
        this.proximaAnalise = this.total + this.passo;
      }
    }
    return tokens;
  }

  #bit(inicio) {
    let a1 = 0, a2 = 0, b1 = 0, b2 = 0;
    let posicao = (inicio + this.deslocamento) % this.buffer.length;
    for (let i = 0; i < this.janela; i++) {
      const amostra = this.buffer[posicao];
      posicao = (posicao + 1) % this.buffer.length;
      const a = amostra + this.coeficientes[0] * a1 - a2;
      const b = amostra + this.coeficientes[1] * b1 - b2;
      a2 = a1; a1 = a; b2 = b1; b1 = b;
    }
    const escala = 4 / (this.janela * this.janela);
    const p0 = Math.max(0, (a1 * a1 + a2 * a2 - this.coeficientes[0] * a1 * a2) * escala);
    const p1 = Math.max(0, (b1 * b1 + b2 * b2 - this.coeficientes[1] * b1 * b2) * escala);
    if (Math.max(p0, p1) < this.config.amplitudeMinima ** 2
        || Math.abs(p1 - p0) / (p1 + p0) < this.config.contrasteMinimo) return null;
    return p1 > p0 ? 1 : 0;
  }

  #cabecalho(inicio) {
    return BITS_CABECALHO.every((bit, i) => this.#bit(inicio + i * this.n) === bit);
  }

  #avancar(tokens) {
    while (true) {
      if (this.candidato === null) {
        if (this.total < this.proximoInicio + (BITS_CABECALHO.length + 1) * this.n) return;
        if (!this.#cabecalho(this.proximoInicio)) { this.proximoInicio += this.passo; continue; }
        // Centro da faixa de fases válidas: evita travar na primeira borda do preâmbulo.
        const fases = [this.proximoInicio];
        for (let i = 1; i <= 8; i++) {
          const fase = this.proximoInicio + i * this.passo;
          if (this.#cabecalho(fase)) fases.push(fase);
        }
        this.candidato = Math.round((fases[0] + fases.at(-1)) / 2);
      }
      if (this.total < this.candidato + BITS_QUADRO * this.n) return;
      const bytes = new Uint8Array(BYTES_QUADRO);
      let valido = true;
      for (let i = 0; i < BITS_QUADRO; i++) {
        const bit = this.#bit(this.candidato + i * this.n);
        if (bit === null) { valido = false; break; }
        bytes[i >> 3] = (bytes[i >> 3] << 1) | bit;
      }
      try {
        if (!valido) throw new Error('Símbolo sem contraste suficiente.');
        tokens.push(lerQuadro(bytes));
        this.proximoInicio = this.candidato + BITS_QUADRO * this.n;
      } catch {
        this.descartados++;
        this.proximoInicio = this.candidato + this.n;
      }
      this.candidato = null;
    }
  }
}
