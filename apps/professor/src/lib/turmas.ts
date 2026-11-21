import type { Turma } from "./tipos";

export type OrdemTurmas = "alfabetica" | "recentes";

/** Minúsculas, sem acentos e com espaços simples: "Distribuídos  " e "distribuidos" ficam iguais. */
function normalizar(texto: string): string {
  return texto
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Turmas em que cada palavra da busca é o começo de alguma palavra da disciplina ou do nome, em
 * qualquer ordem: "dist" encontra "Sistemas Distribuídos" e "a engenharia" encontra
 * "Engenharia de Software · Turma A", mas "ware" não encontra "Software". Busca vazia devolve todas.
 */
export function filtrarTurmas(turmas: Turma[], busca: string): Turma[] {
  const procuradas = normalizar(busca).split(" ").filter(Boolean);
  if (procuradas.length === 0) return turmas;
  return turmas.filter((turma) => {
    const palavras = normalizar(`${turma.disciplina} ${turma.nome}`).split(" ");
    return procuradas.every((procurada) => palavras.some((palavra) => palavra.startsWith(procurada)));
  });
}

// "Turma 2" antes de "Turma 10", sem diferenciar maiúsculas nem acentos
const comparador = new Intl.Collator("pt-BR", { sensitivity: "base", numeric: true });

function alfabetica(a: Turma, b: Turma): number {
  return comparador.compare(a.disciplina, b.disciplina) || comparador.compare(a.nome, b.nome);
}

/** Nova lista ordenada por disciplina e turma, ou pelas criadas mais recentemente. */
export function ordenarTurmas(turmas: Turma[], ordem: OrdemTurmas): Turma[] {
  if (ordem === "alfabetica") return turmas.toSorted(alfabetica);
  return turmas.toSorted(
    (a, b) => Date.parse(b.criadaEm) - Date.parse(a.criadaEm) || alfabetica(a, b),
  );
}
