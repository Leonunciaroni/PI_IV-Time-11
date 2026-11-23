/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
import { describe, expect, it } from "vitest";
import {
  alturaDaBarra,
  aulaAbaixoDoMinimo,
  dataDaAula,
  descricaoDaAula,
  formatarPercentual,
  rotuloDoModo,
} from "./dashboard";
import type { AulaEstatistica } from "./tipos";

const aula: AulaEstatistica = {
  sessaoId: "s1",
  // 19:00 em Brasília, ainda dia 19 lá (22:00 UTC)
  data: "2026-10-19T22:00:00Z",
  modo: "QRCODE",
  presentes: 27,
  matriculados: 30,
  percentual: 90,
};

describe("formatarPercentual", () => {
  it("usa vírgula decimal e omite a casa quando é zero", () => {
    expect(formatarPercentual(87.5)).toBe("87,5%");
    expect(formatarPercentual(90)).toBe("90%");
    expect(formatarPercentual(0)).toBe("0%");
    expect(formatarPercentual(66.7)).toBe("66,7%");
  });
});

describe("rotuloDoModo", () => {
  it("traduz os três modos", () => {
    expect(rotuloDoModo("CLASSICA")).toBe("Clássica");
    expect(rotuloDoModo("QRCODE")).toBe("QR Code");
    expect(rotuloDoModo("SOM")).toBe("Som");
  });
});

describe("dataDaAula", () => {
  it("mostra dia e mês no horário de Brasília", () => {
    expect(dataDaAula(aula.data)).toBe("19/10");
    // 01:30 UTC do dia 20 ainda é dia 19 em Brasília
    expect(dataDaAula("2026-10-20T01:30:00Z")).toBe("19/10");
  });
});

describe("alturaDaBarra", () => {
  it("segue o percentual, limitado entre 0 e 100", () => {
    expect(alturaDaBarra(90)).toBe(90);
    expect(alturaDaBarra(100)).toBe(100);
    expect(alturaDaBarra(130)).toBe(100);
  });

  it("deixa a aula de 0% com uma barra mínima visível", () => {
    expect(alturaDaBarra(0)).toBe(2);
    expect(alturaDaBarra(-5)).toBe(2);
  });
});

describe("aulaAbaixoDoMinimo", () => {
  it("destaca só a aula com percentual menor que o mínimo", () => {
    expect(aulaAbaixoDoMinimo({ ...aula, percentual: 74.9 }, 75)).toBe(true);
    expect(aulaAbaixoDoMinimo({ ...aula, percentual: 75 }, 75)).toBe(false);
    expect(aulaAbaixoDoMinimo(aula, 75)).toBe(false);
  });
});

describe("descricaoDaAula", () => {
  it("resume data, modo e presentes para o leitor de tela", () => {
    expect(descricaoDaAula(aula)).toBe("19/10, QR Code: 27 de 30 presentes (90%)");
  });
});
