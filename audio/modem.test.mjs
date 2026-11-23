// Autoria: Diogo Gonçalves Tonhosolo | RA: 25894007
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { crc16, montarQuadro, lerQuadro, codificarAmostras, criarModem, DecodificadorFsk, configurar, MARGEM_SEGUNDOS } from './modem.mjs';

function concatenar(...partes) {
  const resultado = new Float32Array(partes.reduce((n, p) => n + p.length, 0));
  let inicio = 0;
  for (const parte of partes) { resultado.set(parte, inicio); inicio += parte.length; }
  return resultado;
}

function decodificar(sinal, taxa = 48000, opcoes = {}, blocos = [128]) {
  const decoder = new DecodificadorFsk(taxa, opcoes);
  const tokens = [];
  for (let i = 0, b = 0; i < sinal.length; b++) {
    const fim = Math.min(sinal.length, i + blocos[b % blocos.length]);
    tokens.push(...decoder.processar(sinal.subarray(i, fim)));
    i = fim;
  }
  return { tokens, decoder };
}

function ruido(tamanho, amplitude, semente = 12345) {
  const sinal = new Float32Array(tamanho);
  let estado = semente;
  for (let i = 0; i < tamanho; i++) {
    estado = (Math.imul(estado, 1664525) + 1013904223) >>> 0;
    sinal[i] = (estado / 0x100000000 * 2 - 1) * amplitude;
  }
  return sinal;
}

test('CRC-16 confere com o vetor conhecido 123456789 = 0x29b1', () => {
  assert.equal(crc16(new TextEncoder().encode('123456789')), 0x29b1);
});

test('quadro tem ordem de bytes e CRC interoperáveis com a referência independente', () => {
  // CRC de 01 30 30 31 32 33 34, conferido com binascii.crc_hqx(payload, 0xffff).
  assert.equal(Buffer.from(montarQuadro('001234')).toString('hex'), '55555555d39101303031323334bd44');
});

test('enquadramento preserva zeros à esquerda e aceita apenas seis dígitos ASCII', () => {
  for (const token of ['000000', '000123', '123456', '999999']) {
    const bytes = montarQuadro(token);
    assert.equal(bytes.length, 15);
    assert.deepEqual(Array.from(bytes.subarray(0, 7)), [0x55, 0x55, 0x55, 0x55, 0xd3, 0x91, 1]);
    assert.equal(lerQuadro(bytes), token);
  }
  for (const token of ['', '12345', '1234567', '12 456', '１２３４５６', 123456, null]) {
    assert.throws(() => montarQuadro(token), /seis dígitos/);
  }
});

test('qualquer bit isolado alterado no quadro é recusado', () => {
  const original = montarQuadro('123456');
  for (let bit = 0; bit < original.length * 8; bit++) {
    const alterado = original.slice(); alterado[bit >> 3] ^= 1 << (bit & 7);
    assert.throws(() => lerQuadro(alterado), `bit ${bit}`);
  }
  assert.throws(() => lerQuadro(original.subarray(1)), /Tamanho/);
});

test('conteúdo não numérico é recusado mesmo com CRC recalculado', () => {
  const bytes = montarQuadro('123456'); bytes[7] = 65;
  const crc = crc16(bytes.subarray(6, 13)); bytes[13] = crc >> 8; bytes[14] = crc & 255;
  assert.throws(() => lerQuadro(bytes), /Conteúdo/);
});

for (const taxa of [44100, 48000, 96000]) {
  test(`FSK decodifica PCM em ${taxa} Hz com blocos de captura irregulares`, () => {
    const sinal = codificarAmostras('001234', taxa);
    assert.deepEqual(decodificar(sinal, taxa, {}, [1, 128, 511, 2048, 37]).tokens, ['001234']);
    assert.ok(sinal.every((x) => Math.abs(x) <= 1));
    const margem = Math.ceil(taxa * MARGEM_SEGUNDOS);
    assert.ok(sinal.subarray(0, margem).every((x) => x === 0));
    assert.ok(sinal.subarray(-margem).every((x) => x === 0));
  });
}

test('frequências, ordem dos tons e duração dos símbolos são configuráveis', () => {
  for (const opcoes of [
    { frequencia0: 18000, frequencia1: 19000, duracaoSimboloMs: 30 },
    { frequencia0: 19500, frequencia1: 18500, duracaoSimboloMs: 10 },
  ]) {
    assert.deepEqual(decodificar(codificarAmostras('654321', 48000, opcoes), 48000, opcoes).tokens, ['654321']);
  }
});

test('adquire o quadro após silêncio e em diferentes fases de amostragem', () => {
  for (const atraso of [1, 89, 479, 997, 6031]) {
    const sinal = concatenar(new Float32Array(atraso), codificarAmostras('123456'));
    assert.deepEqual(decodificar(sinal).tokens, ['123456'], `atraso ${atraso}`);
  }
});

test('decodifica sinal atenuado com ruído e eco sintéticos', () => {
  const original = codificarAmostras('987654');
  const sinal = ruido(original.length + 18000, 0.015);
  const inicio = 7001, eco = 311;
  for (let i = 0; i < original.length; i++) {
    sinal[inicio + i] += original[i] * 0.12;
    sinal[inicio + i + eco] += original[i] * 0.025;
  }
  assert.deepEqual(decodificar(sinal).tokens, ['987654']);
});

test('tolera pequena diferença de relógio entre emissor e receptor', () => {
  const original = codificarAmostras('102030');
  for (const fator of [0.999, 1.001]) {
    const sinal = new Float32Array(Math.floor(original.length / fator));
    for (let i = 0; i < sinal.length; i++) {
      const pos = i * fator, inteiro = Math.floor(pos), fracao = pos - inteiro;
      sinal[i] = original[inteiro] * (1 - fracao) + (original[inteiro + 1] ?? 0) * fracao;
    }
    assert.deepEqual(decodificar(sinal).tokens, ['102030'], `fator ${fator}`);
  }
});

test('ignora silêncio, ruído e tom contínuo sem enquadramento', () => {
  assert.deepEqual(decodificar(new Float32Array(96000)).tokens, []);
  assert.deepEqual(decodificar(ruido(144000, 0.2)).tokens, []);
  const tom = Float32Array.from({ length: 144000 }, (_, i) => Math.sin(2 * Math.PI * 18500 * i / 48000));
  assert.deepEqual(decodificar(tom).tokens, []);
});

test('CRC descarta um símbolo corrompido e o receptor encontra o próximo quadro', () => {
  const alterado = codificarAmostras('123456');
  const byte = montarQuadro('123456');
  const bit = 7 * 8 + 1;
  const anterior = (byte[bit >> 3] >> (7 - (bit & 7))) & 1;
  const frequencia = anterior ? 18500 : 19500;
  const inicio = Math.ceil(48000 * MARGEM_SEGUNDOS) + bit * 960;
  for (let i = 0; i < 960; i++) alterado[inicio + i] = Math.sin(2 * Math.PI * frequencia * i / 48000);
  const resultado = decodificar(concatenar(alterado, codificarAmostras('000007')));
  assert.deepEqual(resultado.tokens, ['000007']);
  assert.ok(resultado.decoder.descartados >= 1);
});

test('ressincroniza depois de uma transmissão interrompida', () => {
  const cortado = codificarAmostras('111111').subarray(0, 65000);
  const sinal = concatenar(cortado, new Float32Array(15000), codificarAmostras('222222'));
  assert.deepEqual(decodificar(sinal).tokens, ['222222']);
});

test('processa vários quadros no mesmo bloco sem crescer o buffer circular', () => {
  const decoder = new DecodificadorFsk();
  const capacidade = decoder.buffer.length;
  const tokens = ['000001', '000002', '000003', '000004'];
  const sinal = concatenar(...tokens.map((token) => codificarAmostras(token)));
  assert.deepEqual(decoder.processar(sinal), tokens);
  assert.equal(decoder.buffer.length, capacidade);
  decoder.reiniciar();
  assert.equal(decoder.total, 0);
  assert.deepEqual(decoder.processar(codificarAmostras('000005')), ['000005']);
});

test('adaptador devolve AudioBuffer mono com margem para o emissor existente', () => {
  const contexto = { sampleRate: 48000, createBuffer(canais, length, sampleRate) {
    assert.equal(canais, 1);
    const dados = new Float32Array(length);
    return { duration: length / sampleRate, getChannelData: () => dados };
  } };
  const buffer = criarModem().codificar('123456', contexto);
  assert.equal(buffer.duration, 2.52);
  assert.deepEqual(decodificar(buffer.getChannelData(0)).tokens, ['123456']);
});

test('recusa configuração inválida e taxa abaixo de Nyquist', () => {
  for (const opcoes of [{ frequencia0: NaN }, { frequencia1: 23000 }, { frequencia0: 1000 },
    { frequencia0: 19500 }, { duracaoSimboloMs: 0 }, { contrasteMinimo: 1 }, { amplitudeMinima: -1 }]) {
    assert.throws(() => configurar(opcoes), RangeError);
  }
  assert.throws(() => new DecodificadorFsk(32000), /Nyquist/);
  assert.throws(() => codificarAmostras('123456', 22050), /Nyquist/);
  assert.throws(() => new DecodificadorFsk().processar([0, 1]), /Float32Array/);
});
