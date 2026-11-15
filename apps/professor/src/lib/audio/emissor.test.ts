// Mesmos casos de audio/emissor.test.mjs (#46), portados do node:test para o Vitest
import { afterEach, describe, expect, it, vi } from "vitest";
import { EmissorToken, type ContextoDeEmissao, type OpcoesEmissor } from "./emissor";

interface FonteFalsa {
  inicio?: number;
  fim?: number;
  onended?: () => void;
}

function ambiente(opcoes: Partial<OpcoesEmissor> = {}) {
  const fontes: FonteFalsa[] = [];
  const rampas: unknown[][] = [];
  const tokens: number[] = [];
  const contexto = {
    state: "running",
    currentTime: 10,
    destination: {},
    async resume() {},
    createBufferSource() {
      const fonte = {
        connect: () => ({ connect() {} }),
        disconnect() {},
        start(t: number) {
          fonte.inicio = t;
        },
        stop(t: number) {
          fonte.fim = t;
        },
      } as FonteFalsa & Record<string, unknown>;
      fontes.push(fonte);
      return fonte;
    },
    createGain() {
      return {
        disconnect() {},
        gain: {
          setValueAtTime: (v: number, t: number) => rampas.push(["valor", v, t]),
          linearRampToValueAtTime: (v: number, t: number) => rampas.push(["rampa", v, t]),
          cancelAndHoldAtTime: (t: number) => rampas.push(["segurar", t]),
        },
      };
    },
  } as unknown as ContextoDeEmissao;
  const emissor = new EmissorToken({
    contexto,
    periodoSegundos: 5,
    gerarToken: async (t) => {
      tokens.push(t);
      return "123456";
    },
    codificar: async () => ({ duration: 1 }) as AudioBuffer,
    ...opcoes,
  });
  return { emissor, fontes, rampas, tokens };
}

describe("EmissorToken", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("emite na janela atual e nas fronteiras, com volume e rampas do spike", async () => {
    vi.useFakeTimers({ now: 1200 });
    const { emissor, fontes, rampas, tokens } = ambiente();
    await emissor.iniciar();
    expect(fontes).toHaveLength(1);
    expect(rampas).toEqual([
      ["valor", 0, 10],
      ["rampa", 0.3, 10.05],
      ["valor", 0.3, 10.95],
      ["rampa", 0, 11],
    ]);
    await vi.advanceTimersByTimeAsync(3800);
    expect(tokens).toEqual([1200, 5000]);
    await vi.advanceTimersByTimeAsync(5000);
    expect(tokens).toEqual([1200, 5000, 10000]);
    emissor.parar();
  });

  it("usa o volume atual a cada quadro", async () => {
    vi.useFakeTimers({ now: 0 });
    const { emissor, rampas } = ambiente();
    await emissor.iniciar();
    emissor.volume = 0.8;
    await vi.advanceTimersByTimeAsync(5000);
    expect(rampas.slice(-3, -2)).toEqual([["rampa", 0.8, 10.05]]);
    emissor.parar();
  });

  it("não corta um quadro ao iniciar perto da próxima janela", async () => {
    vi.useFakeTimers({ now: 4800 });
    const { emissor, fontes } = ambiente();
    await emissor.iniciar();
    expect(fontes).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(200);
    expect(fontes).toHaveLength(1);
    emissor.parar();
  });

  it("parar cancela o loop e segura o ganho antes do fade out", async () => {
    vi.useFakeTimers({ now: 0 });
    const { emissor, fontes, rampas } = ambiente();
    await emissor.iniciar();
    await emissor.iniciar();
    emissor.parar();
    expect(rampas.slice(-2)).toEqual([
      ["segurar", 10],
      ["rampa", 0, 10.05],
    ]);
    expect(fontes[0].fim).toBe(10.05);
    await vi.advanceTimersByTimeAsync(10000);
    expect(fontes).toHaveLength(1);
    fontes[0].onended?.();
    expect(emissor.fontes.size).toBe(0);
  });

  it("descarta o token se a geração atravessar a janela", async () => {
    vi.useFakeTimers({ now: 4900 });
    const { emissor, fontes } = ambiente({
      gerarToken: async () => {
        vi.setSystemTime(5100);
        return "123456";
      },
    });
    await emissor.iniciar();
    expect(fontes).toHaveLength(0);
    emissor.parar();
  });

  it("parar durante a codificação impede emissão tardia", async () => {
    let resolver: (b: AudioBuffer) => void = () => {};
    const { emissor, fontes } = ambiente({
      agora: () => 0,
      codificar: () =>
        new Promise<AudioBuffer>((resolve) => {
          resolver = resolve;
        }),
    });
    const inicio = emissor.iniciar();
    await new Promise((r) => setTimeout(r, 0));
    emissor.parar();
    resolver({ duration: 1 } as AudioBuffer);
    await inicio;
    expect(fontes).toHaveLength(0);
  });

  it("falhas de codificação param a emissão e notificam o chamador", async () => {
    const falha = new Error("Falha do modem");
    const erros: unknown[] = [];
    const { emissor } = ambiente({
      codificar: async () => {
        throw falha;
      },
      aoErro: (erro) => erros.push(erro),
    });
    await expect(emissor.iniciar()).rejects.toBe(falha);
    expect(emissor.ativo).toBe(false);
    expect(erros).toEqual([falha]);
  });

  it.each([0, 0.1, 5, 6, NaN])("rejeita quadro de %s s, que não cabe na janela ou nas rampas", async (duration) => {
    const { emissor } = ambiente({ codificar: async () => ({ duration }) as AudioBuffer });
    await expect(emissor.iniciar()).rejects.toBeInstanceOf(RangeError);
    expect(emissor.ativo).toBe(false);
  });

  it("recusa volume fora de 0 a 1 e janela inválida", () => {
    expect(() => ambiente({ volume: 1.5 })).toThrow(/Volume/);
    expect(() => ambiente({ periodoSegundos: 0 })).toThrow(/Janela/);
  });
});
