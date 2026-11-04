import { describe, expect, it } from "vitest";
import { contarPresentes, filtrarPorNome, marcarTodos } from "./presencas";

const alunos = [
  { id: "a1", nome: "Ana Souza", matricula: "24001234" },
  { id: "a2", nome: "Bruno Lima", matricula: "24001777" },
  { id: "a3", nome: "João Ribeiro", matricula: "24002084" },
  { id: "a4", nome: "Larissa Mendes", matricula: "24002096" },
];

describe("filtrarPorNome", () => {
  it("devolve todos com a busca vazia ou só com espaços", () => {
    expect(filtrarPorNome(alunos, "")).toEqual(alunos);
    expect(filtrarPorNome(alunos, "   ")).toEqual(alunos);
  });

  it("encontra parte do nome sem diferenciar maiúsculas nem acentos", () => {
    expect(filtrarPorNome(alunos, "joao").map((a) => a.id)).toEqual(["a3"]);
    expect(filtrarPorNome(alunos, "SOUZA").map((a) => a.id)).toEqual(["a1"]);
    expect(filtrarPorNome(alunos, "  li ").map((a) => a.id)).toEqual(["a2"]);
  });

  it("devolve lista vazia quando ninguém confere", () => {
    expect(filtrarPorNome(alunos, "xyz")).toEqual([]);
  });
});

describe("marcarTodos", () => {
  it("marca todos os alunos com o mesmo valor", () => {
    expect(marcarTodos(alunos, true)).toEqual({ a1: true, a2: true, a3: true, a4: true });
    expect(marcarTodos(alunos, false)).toEqual({ a1: false, a2: false, a3: false, a4: false });
  });
});

describe("contarPresentes", () => {
  it("conta só quem está marcado como presente, entre os alunos da lista", () => {
    expect(contarPresentes(alunos, { a1: true, a2: false, a4: true })).toBe(2);
    expect(contarPresentes(alunos, {})).toBe(0);
    // Marcação de alguém que saiu da turma não entra na conta
    expect(contarPresentes(alunos, { a1: true, x9: true })).toBe(1);
  });
});
