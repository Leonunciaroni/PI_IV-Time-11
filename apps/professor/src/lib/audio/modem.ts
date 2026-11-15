// Parte de emissão do modem FSK v1 (#26), portada de audio/modem.mjs (PR #124, branch
// feature/modem-audio-fsk, commit b0246a6). A imagem Docker do professor só enxerga apps/professor,
// por isso a cópia. O decodificador fica no módulo original, que é do app do aluno.
// Se o enquadramento ou o perfil mudarem no #124 (teste físico, #13), atualize esta cópia.

/** Perfil inicial do docs/audio-modem.md; os dois lados precisam usar os mesmos tons e duração. */
export interface ConfiguracaoModem {
  frequencia0: number;
  frequencia1: number;
  duracaoSimboloMs: number;
  amplitudeMinima: number;
  contrasteMinimo: number;
}

export const CONFIGURACAO_PADRAO: Readonly<ConfiguracaoModem> = Object.freeze({
  frequencia0: 18500,
  frequencia1: 19500,
  duracaoSimboloMs: 20,
  amplitudeMinima: 0.0001,
  contrasteMinimo: 0.6,
});

/** Silêncio antes e depois do quadro, para as rampas de 50 ms do emissor não atenuarem símbolos. */
export const MARGEM_SEGUNDOS = 0.06;
export const BYTES_QUADRO = 15;
const CABECALHO = Object.freeze([0x55, 0x55, 0x55, 0x55, 0xd3, 0x91]);
const VERSAO = 1;

// Bit mais significativo primeiro
const bitsDe = (bytes: ArrayLike<number>): number[] =>
  Array.from(bytes).flatMap((byte) => Array.from({ length: 8 }, (_, i) => (byte >> (7 - i)) & 1));

export function configurar(opcoes: Partial<ConfiguracaoModem> = {}, taxaAmostragem = 48000) {
  const config: ConfiguracaoModem = { ...CONFIGURACAO_PADRAO, ...opcoes };
  for (const chave of Object.keys(CONFIGURACAO_PADRAO) as (keyof ConfiguracaoModem)[]) {
    if (!Number.isFinite(config[chave])) throw new RangeError(`Parâmetro inválido: ${chave}.`);
  }
  if (
    config.frequencia0 < 18000 ||
    config.frequencia0 > 20000 ||
    config.frequencia1 < 18000 ||
    config.frequencia1 > 20000
  ) {
    throw new RangeError("As duas frequências devem estar entre 18000 e 20000 Hz.");
  }
  if (config.duracaoSimboloMs < 10 || config.duracaoSimboloMs > 100) {
    throw new RangeError("A duração do símbolo deve estar entre 10 e 100 ms.");
  }
  if (Math.abs(config.frequencia1 - config.frequencia0) < 2000 / config.duracaoSimboloMs) {
    throw new RangeError("As frequências estão muito próximas para a duração do símbolo.");
  }
  if (
    config.amplitudeMinima <= 0 ||
    config.amplitudeMinima > 1 ||
    config.contrasteMinimo <= 0 ||
    config.contrasteMinimo >= 1
  ) {
    throw new RangeError("Limiar de amplitude ou contraste inválido.");
  }
  if (
    !Number.isInteger(taxaAmostragem) ||
    taxaAmostragem <= 0 ||
    taxaAmostragem > 384000 ||
    Math.max(config.frequencia0, config.frequencia1) + 100 >= taxaAmostragem / 2
  ) {
    throw new RangeError("Taxa de amostragem insuficiente para a faixa escolhida (Nyquist).");
  }
  return {
    ...config,
    taxaAmostragem,
    amostrasSimbolo: Math.round((taxaAmostragem * config.duracaoSimboloMs) / 1000),
  };
}

/** CRC-16/IBM-3740 (CCITT-FALSE): poly 0x1021, init 0xffff, sem reflexão nem xor final. */
export function crc16(bytes: ArrayLike<number>): number {
  let crc = 0xffff;
  for (let n = 0; n < bytes.length; n++) {
    crc ^= bytes[n] << 8;
    for (let i = 0; i < 8; i++) crc = ((crc << 1) ^ (crc & 0x8000 ? 0x1021 : 0)) & 0xffff;
  }
  return crc;
}

/** Preâmbulo 55×4, sincronismo D3 91, versão, token em ASCII e CRC dos bytes 6–12. */
export function montarQuadro(token: string): Uint8Array {
  if (typeof token !== "string" || !/^[0-9]{6}$/.test(token)) {
    throw new TypeError("O token deve conter exatamente seis dígitos.");
  }
  const bytes = new Uint8Array(BYTES_QUADRO);
  bytes.set(CABECALHO);
  bytes[6] = VERSAO;
  for (let i = 0; i < 6; i++) bytes[7 + i] = token.charCodeAt(i);
  const crc = crc16(bytes.subarray(6, 13));
  bytes[13] = crc >> 8;
  bytes[14] = crc & 255;
  return bytes;
}

/** PCM mono do quadro completo, com as margens de silêncio e fase contínua entre os tons. */
export function codificarAmostras(
  token: string,
  taxaAmostragem = 48000,
  opcoes: Partial<ConfiguracaoModem> = {},
): Float32Array {
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
    fase = (fase + (2 * Math.PI * frequencia) / taxaAmostragem) % (2 * Math.PI);
  }
  return sinal;
}

/** O que o modem precisa do AudioContext para montar o buffer. */
export interface ContextoDeBuffer {
  sampleRate: number;
  createBuffer(canais: number, tamanho: number, taxa: number): AudioBuffer;
}

/** Adaptador para o EmissorToken: o volume é controlado pelo emissor, não pelo modem. */
export function criarModem(opcoes: Partial<ConfiguracaoModem> = {}) {
  return {
    codificar(token: string, contexto: ContextoDeBuffer): AudioBuffer {
      const sinal = codificarAmostras(token, contexto.sampleRate, opcoes);
      const buffer = contexto.createBuffer(1, sinal.length, contexto.sampleRate);
      buffer.getChannelData(0).set(sinal);
      return buffer;
    },
  };
}
