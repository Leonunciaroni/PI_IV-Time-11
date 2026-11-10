import { test } from "node:test";
import assert from "node:assert/strict";
import { EmissorToken } from "./emissor.mjs";

function ambiente(opcoes = {}) {
  const fontes = [];
  const rampas = [];
  const tokens = [];
  const contexto = {
    state: "running", currentTime: 10, destination: {},
    async resume() {},
    createBufferSource() {
      const fonte = { connect() { return { connect() {} }; }, disconnect() {},
        start(t) { this.inicio = t; }, stop(t) { this.fim = t; } };
      fontes.push(fonte);
      return fonte;
    },
    createGain() {
      return { disconnect() {}, gain: {
        setValueAtTime(v, t) { rampas.push(["valor", v, t]); },
        linearRampToValueAtTime(v, t) { rampas.push(["rampa", v, t]); },
        cancelAndHoldAtTime(t) { rampas.push(["segurar", t]); },
      } };
    },
  };
  const emissor = new EmissorToken({ contexto, periodoSegundos: 5,
    gerarToken: async (t) => { tokens.push(t); return "123456"; },
    codificar: async () => ({ duration: 1 }), ...opcoes });
  return { emissor, fontes, rampas, tokens, contexto };
}

test("emite na janela atual e nas fronteiras, com volume e rampas do spike", async (t) => {
  t.mock.timers.enable({ apis: ["Date", "setTimeout"], now: 1200 });
  const { emissor, fontes, rampas, tokens } = ambiente();
  t.after(() => emissor.parar());
  await emissor.iniciar();
  assert.equal(fontes.length, 1);
  assert.deepEqual(rampas, [["valor", 0, 10], ["rampa", 0.3, 10.05],
    ["valor", 0.3, 10.95], ["rampa", 0, 11]]);
  t.mock.timers.tick(3800);
  await new Promise(setImmediate);
  assert.deepEqual(tokens, [1200, 5000]);
  t.mock.timers.tick(5000);
  await new Promise(setImmediate);
  assert.deepEqual(tokens, [1200, 5000, 10000]);
});

test("não corta um quadro ao iniciar perto da próxima janela", async (t) => {
  t.mock.timers.enable({ apis: ["Date", "setTimeout"], now: 4800 });
  const { emissor, fontes } = ambiente();
  t.after(() => emissor.parar());
  await emissor.iniciar();
  assert.equal(fontes.length, 0);
  t.mock.timers.tick(200);
  await new Promise(setImmediate);
  assert.equal(fontes.length, 1);
});

test("parar cancela o loop e segura o ganho antes do fade out", async (t) => {
  t.mock.timers.enable({ apis: ["Date", "setTimeout"], now: 0 });
  const { emissor, fontes, rampas } = ambiente();
  await emissor.iniciar();
  await emissor.iniciar();
  emissor.parar();
  assert.deepEqual(rampas.slice(-2), [["segurar", 10], ["rampa", 0, 10.05]]);
  assert.equal(fontes[0].fim, 10.05);
  t.mock.timers.tick(10000);
  await new Promise(setImmediate);
  assert.equal(fontes.length, 1);
  fontes[0].onended();
  assert.equal(emissor.fontes.size, 0);
});

test("descarta o token se a geração atravessar a janela", async (t) => {
  t.mock.timers.enable({ apis: ["Date", "setTimeout"], now: 4900 });
  const { emissor, fontes } = ambiente({ gerarToken: async () => {
    t.mock.timers.tick(200);
    return "123456";
  } });
  t.after(() => emissor.parar());
  await emissor.iniciar();
  assert.equal(fontes.length, 0);
});

test("parar durante a codificação impede emissão tardia", async () => {
  let resolver;
  const { emissor, fontes } = ambiente({ agora: () => 0,
    codificar: () => new Promise((resolve) => { resolver = resolve; }) });
  const inicio = emissor.iniciar();
  await new Promise(setImmediate);
  emissor.parar();
  resolver({ duration: 1 });
  await inicio;
  assert.equal(fontes.length, 0);
});

test("falhas de codificação param a emissão e notificam o chamador", async () => {
  const falha = new Error("Falha do modem");
  const erros = [];
  const { emissor } = ambiente({ codificar: async () => { throw falha; },
    aoErro: (erro) => erros.push(erro) });
  await assert.rejects(emissor.iniciar(), falha);
  assert.equal(emissor.ativo, false);
  assert.deepEqual(erros, [falha]);
});

test("rejeita quadros que não cabem na janela ou nas rampas", async () => {
  for (const duration of [0, 0.1, 5, 6, NaN]) {
    const { emissor } = ambiente({ codificar: async () => ({ duration }) });
    await assert.rejects(emissor.iniciar(), RangeError);
    assert.equal(emissor.ativo, false);
  }
});
