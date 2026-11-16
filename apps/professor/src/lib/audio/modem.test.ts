import { describe, expect, it } from "vitest";
import { codificarAmostras, configurar, crc16, criarModem, MARGEM_SEGUNDOS, montarQuadro } from "./modem";

const hex = (bytes: Uint8Array) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

describe("modem FSK (emissão)", () => {
  it("CRC-16 confere com o vetor conhecido 123456789 = 0x29b1", () => {
    expect(crc16(new TextEncoder().encode("123456789"))).toBe(0x29b1);
  });

  it("monta o quadro de 001234 igual ao exemplo do docs/audio-modem.md", () => {
    expect(hex(montarQuadro("001234"))).toBe("55555555d39101303031323334bd44");
  });

  it("preserva zeros à esquerda e aceita apenas seis dígitos", () => {
    expect(Array.from(montarQuadro("000000").subarray(7, 13))).toEqual(Array(6).fill(0x30));
    for (const token of ["", "12345", "1234567", "12 456", "１２３４５６"]) {
      expect(() => montarQuadro(token)).toThrow(/seis dígitos/);
    }
  });

  it.each([44100, 48000, 96000])("gera 2,52 s de sinal em %i Hz, com margens silenciosas", (taxa) => {
    const sinal = codificarAmostras("001234", taxa);
    const margem = Math.ceil(taxa * MARGEM_SEGUNDOS);
    const simbolo = Math.round((taxa * 20) / 1000);
    expect(sinal.length).toBe(2 * margem + 120 * simbolo);
    expect(sinal.subarray(0, margem).every((x) => x === 0)).toBe(true);
    expect(sinal.subarray(-margem).every((x) => x === 0)).toBe(true);
    expect(sinal.every((x) => Math.abs(x) <= 1)).toBe(true);
  });

  it("recusa faixa fora de 18–20 kHz, tons próximos demais e taxa abaixo de Nyquist", () => {
    expect(() => configurar({ frequencia0: 17000 })).toThrow(/18000 e 20000/);
    expect(() => configurar({ frequencia0: 19000, frequencia1: 19050 })).toThrow(/próximas/);
    expect(() => configurar({}, 22050)).toThrow(/Nyquist/);
  });

  it("criarModem devolve um AudioBuffer mono com o quadro, na taxa do contexto", () => {
    const canal = { dados: null as Float32Array | null };
    const contexto = {
      sampleRate: 48000,
      createBuffer(canais: number, tamanho: number, taxa: number) {
        expect([canais, taxa]).toEqual([1, 48000]);
        canal.dados = new Float32Array(tamanho);
        return { duration: tamanho / taxa, getChannelData: () => canal.dados } as unknown as AudioBuffer;
      },
    };
    const buffer = criarModem().codificar("001234", contexto);
    expect(buffer.duration).toBeCloseTo(2.52, 2);
    expect(canal.dados).toEqual(codificarAmostras("001234", 48000));
  });
});
