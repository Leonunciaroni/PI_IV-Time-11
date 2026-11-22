import { describe, expect, it } from "vitest";
import type { Turma } from "./tipos";
import { filtrarTurmas, ordenarTurmas } from "./turmas";

function turma(disciplina: string, nome: string, criadaEm = "2026-08-01T12:00:00Z"): Turma {
  return { id: `${disciplina}-${nome}`, disciplina, nome, horarioPadrao: "Seg 19:00", totalAlunos: 0, criadaEm };
}

const nomes = (turmas: Turma[]) => turmas.map((t) => `${t.disciplina} · ${t.nome}`);

describe("filtrarTurmas", () => {
  const turmas = [
    turma("Banco de Dados", "Turma CSV 1117224345"),
    turma("Engenharia de Software I", "Turma A"),
    turma("Engenharia de Software II", "Turma B"),
    turma("Sistemas Distribuídos", "Turma E2E 11161857"),
  ];

  it("devolve todas as turmas quando a busca está vazia ou só tem espaços", () => {
    expect(filtrarTurmas(turmas, "")).toEqual(turmas);
    expect(filtrarTurmas(turmas, "   ")).toEqual(turmas);
  });

  it("procura na disciplina e no nome da turma, sem diferenciar maiúsculas", () => {
    expect(nomes(filtrarTurmas(turmas, "BANCO"))).toEqual(["Banco de Dados · Turma CSV 1117224345"]);
    expect(nomes(filtrarTurmas(turmas, "software ii"))).toEqual(["Engenharia de Software II · Turma B"]);
  });

  it("compara com o começo das palavras, não com letras no meio delas", () => {
    expect(nomes(filtrarTurmas(turmas, "dist"))).toEqual(["Sistemas Distribuídos · Turma E2E 11161857"]);
    expect(filtrarTurmas(turmas, "ware")).toEqual([]);
  });

  it("ignora acentos tanto na busca quanto nos dados", () => {
    expect(nomes(filtrarTurmas(turmas, "distribuidos"))).toEqual(["Sistemas Distribuídos · Turma E2E 11161857"]);
    expect(nomes(filtrarTurmas(turmas, "dádos"))).toEqual(["Banco de Dados · Turma CSV 1117224345"]);
  });

  it("aceita palavras em qualquer ordem, todas precisam aparecer", () => {
    expect(nomes(filtrarTurmas(turmas, "a engenharia"))).toEqual(["Engenharia de Software I · Turma A"]);
    expect(filtrarTurmas(turmas, "engenharia banco")).toEqual([]);
  });

  it("encontra pelo código da turma", () => {
    expect(nomes(filtrarTurmas(turmas, "11161857"))).toEqual(["Sistemas Distribuídos · Turma E2E 11161857"]);
  });
});

describe("ordenarTurmas", () => {
  const turmas = [
    turma("engenharia de Software", "Turma 10", "2026-08-03T12:00:00Z"),
    turma("Banco de Dados", "Turma B", "2026-08-01T12:00:00Z"),
    turma("Engenharia de Software", "Turma 2", "2026-08-05T12:00:00Z"),
    turma("Banco de Dados", "Turma A", "2026-08-05T12:00:00Z"),
  ];

  it("em ordem alfabética, ordena por disciplina e depois pela turma, com números em ordem crescente", () => {
    expect(nomes(ordenarTurmas(turmas, "alfabetica"))).toEqual([
      "Banco de Dados · Turma A",
      "Banco de Dados · Turma B",
      "Engenharia de Software · Turma 2",
      "engenharia de Software · Turma 10",
    ]);
  });

  it("em mais recentes, ordena pela criação e desempata em ordem alfabética", () => {
    expect(nomes(ordenarTurmas(turmas, "recentes"))).toEqual([
      "Banco de Dados · Turma A",
      "Engenharia de Software · Turma 2",
      "engenharia de Software · Turma 10",
      "Banco de Dados · Turma B",
    ]);
  });

  it("não altera a lista recebida", () => {
    const original = [...turmas];
    ordenarTurmas(turmas, "recentes");
    expect(turmas).toEqual(original);
  });
});
