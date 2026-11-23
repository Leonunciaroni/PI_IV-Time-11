/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { describe, expect, it } from "vitest";
import type { ChamadaGuardada } from "./chamadas";
import {
  aplicarModo,
  explicacaoDaTroca,
  mudaAChamada,
  precisaDeSala,
  rotuloDaTroca,
  salaSugerida,
  trocasPermitidas,
} from "./trocaDeModo";

const totp = { segredo: "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", algoritmo: "HmacSHA256", digitos: 6, periodoSegundos: 15, t0: 0 };
const qr: ChamadaGuardada = { sessaoId: "s1", turmaId: "t1", modo: "QRCODE", abertaEm: 100, totp, salaId: "sala-1" };
const som: ChamadaGuardada = { sessaoId: "s1", turmaId: "t1", modo: "SOM", abertaEm: 100, totp };

describe("trocasPermitidas", () => {
  it("segue os critérios da #49: som → QR Code e qualquer modo → clássica", () => {
    expect(trocasPermitidas("SOM")).toEqual(["QRCODE", "CLASSICA"]);
    expect(trocasPermitidas("QRCODE")).toEqual(["CLASSICA"]);
    expect(trocasPermitidas("CLASSICA")).toEqual([]);
  });

  it("só o QR Code pede sala", () => {
    expect(precisaDeSala("QRCODE")).toBe(true);
    expect(precisaDeSala("CLASSICA")).toBe(false);
    expect(precisaDeSala("SOM")).toBe(false);
  });
});

describe("aplicarModo", () => {
  it("troca o modo e a sala sem mexer no TOTP nem na abertura", () => {
    const nova = aplicarModo(som, { modo: "QRCODE", salaId: "sala-2" });
    expect(nova).toEqual({ ...som, modo: "QRCODE", salaId: "sala-2" });
    expect(nova.totp).toBe(som.totp);
  });

  it("sem sala no servidor, tira a sala guardada", () => {
    const nova = aplicarModo(qr, { modo: "CLASSICA", salaId: null });
    expect(nova.modo).toBe("CLASSICA");
    expect("salaId" in nova).toBe(false);
  });

  it("não altera o objeto original", () => {
    aplicarModo(qr, { modo: "CLASSICA", salaId: null });
    expect(qr.modo).toBe("QRCODE");
    expect(qr.salaId).toBe("sala-1");
  });
});

describe("mudaAChamada", () => {
  it("é verdade quando o modo ou a sala mudam nesta chamada", () => {
    expect(mudaAChamada(som, { sessaoId: "s1", modo: "QRCODE", salaId: "sala-1", alteradoEm: 1 })).toBe(true);
    expect(mudaAChamada(qr, { sessaoId: "s1", modo: "QRCODE", salaId: "sala-2", alteradoEm: 1 })).toBe(true);
  });

  it("ignora outra chamada e a resposta que repete o estado atual", () => {
    expect(mudaAChamada(som, { sessaoId: "outra", modo: "CLASSICA", salaId: null, alteradoEm: 1 })).toBe(false);
    expect(mudaAChamada(qr, { sessaoId: "s1", modo: "QRCODE", salaId: "sala-1", alteradoEm: 1 })).toBe(false);
    expect(mudaAChamada(som, { sessaoId: "s1", modo: "SOM", salaId: null, alteradoEm: 1 })).toBe(false);
  });
});

describe("salaSugerida", () => {
  const salas = ["a", "b", "c"];

  it("prefere a sala da chamada, depois a última usada, depois a primeira", () => {
    expect(salaSugerida(salas, "b", "c")).toBe("b");
    expect(salaSugerida(salas, undefined, "c")).toBe("c");
    expect(salaSugerida(salas, undefined, null)).toBe("a");
  });

  it("ignora salas que não existem mais", () => {
    expect(salaSugerida(salas, "apagada", "c")).toBe("c");
    expect(salaSugerida(salas, "apagada", "outra-apagada")).toBe("a");
  });

  it("sem salas cadastradas, não há sugestão", () => {
    expect(salaSugerida([], "b", "c")).toBeNull();
  });
});

describe("textos", () => {
  it("usa o nome do modo de destino", () => {
    expect(rotuloDaTroca("CLASSICA")).toBe("Trocar para clássica");
    expect(rotuloDaTroca("QRCODE")).toBe("Trocar para QR Code");
  });

  it("explica que quem já está presente continua presente", () => {
    for (const destino of ["CLASSICA", "QRCODE", "SOM"] as const) {
      expect(explicacaoDaTroca(destino)).toMatch(/continua presente/);
    }
  });
});
