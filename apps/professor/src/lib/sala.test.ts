/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { describe, expect, it } from "vitest";
import { lerSala, type DadosSala } from "./sala";

const valida: DadosSala = { nome: "H15 - Sala 203", latitude: "-22.8337", longitude: "-47.0525", raioMetros: "30" };

function errosDe(dados: Partial<DadosSala>) {
  const resultado = lerSala({ ...valida, ...dados });
  if (resultado.ok) throw new Error("era esperado erro de validação");
  return resultado.erros;
}

describe("lerSala", () => {
  it("converte os campos do formulário para o corpo da requisição", () => {
    expect(lerSala({ ...valida, nome: "  H15 - Sala 203  " })).toEqual({
      ok: true,
      sala: { nome: "H15 - Sala 203", latitude: -22.8337, longitude: -47.0525, raioMetros: 30 },
    });
  });

  it("aceita vírgula como separador decimal", () => {
    const resultado = lerSala({ ...valida, latitude: "-22,8337", longitude: " -47,0525 ", raioMetros: "12,5" });
    expect(resultado).toEqual({
      ok: true,
      sala: { nome: "H15 - Sala 203", latitude: -22.8337, longitude: -47.0525, raioMetros: 12.5 },
    });
  });

  it("exige o nome", () => {
    expect(errosDe({ nome: "   " })).toEqual({ nome: "Informe o nome da sala." });
  });

  it("exige a latitude e recusa texto que não é número", () => {
    expect(errosDe({ latitude: "" }).latitude).toBe("Informe a latitude.");
    expect(errosDe({ latitude: "-22.8x" }).latitude).toBe("A latitude deve ser um número, como -22.8337.");
  });

  it("aceita latitude de -90 a 90 e recusa o que passa disso", () => {
    expect(lerSala({ ...valida, latitude: "-90" }).ok).toBe(true);
    expect(lerSala({ ...valida, latitude: "90" }).ok).toBe(true);
    expect(errosDe({ latitude: "90.0001" }).latitude).toBe("A latitude deve estar entre -90 e 90.");
    expect(errosDe({ latitude: "-90.0001" }).latitude).toBe("A latitude deve estar entre -90 e 90.");
  });

  it("exige a longitude e recusa texto que não é número", () => {
    expect(errosDe({ longitude: " " }).longitude).toBe("Informe a longitude.");
    expect(errosDe({ longitude: "leste" }).longitude).toBe("A longitude deve ser um número, como -47.0525.");
  });

  it("aceita longitude de -180 a 180 e recusa o que passa disso", () => {
    expect(lerSala({ ...valida, longitude: "-180" }).ok).toBe(true);
    expect(lerSala({ ...valida, longitude: "180" }).ok).toBe(true);
    expect(errosDe({ longitude: "180.5" }).longitude).toBe("A longitude deve estar entre -180 e 180.");
  });

  it("exige raio maior que zero", () => {
    expect(errosDe({ raioMetros: "" }).raioMetros).toBe("Informe o raio.");
    expect(errosDe({ raioMetros: "trinta" }).raioMetros).toBe("O raio deve ser um número de metros, como 30.");
    expect(errosDe({ raioMetros: "0" }).raioMetros).toBe("O raio deve ser maior que zero.");
    expect(errosDe({ raioMetros: "-5" }).raioMetros).toBe("O raio deve ser maior que zero.");
  });

  it("reúne os erros de todos os campos de uma vez", () => {
    expect(Object.keys(errosDe({ nome: "", latitude: "", longitude: "", raioMetros: "" })).sort()).toEqual([
      "latitude",
      "longitude",
      "nome",
      "raioMetros",
    ]);
  });
});
