// Lógica da lista de presença da chamada clássica (P6), sem interface.

/** Presença marcada por aluno: true presente, false ausente; sem registro conta como ausente. */
export type Marcacoes = Record<string, boolean>;

interface ComNome {
  id: string;
  nome: string;
}

function semAcento(texto: string): string {
  return texto.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
}

/** Alunos cujo nome contém a busca, sem diferenciar maiúsculas nem acentos. */
export function filtrarPorNome<T extends ComNome>(alunos: T[], busca: string): T[] {
  const termo = semAcento(busca.trim());
  if (!termo) return alunos;
  return alunos.filter((aluno) => semAcento(aluno.nome).includes(termo));
}

export function marcarTodos(alunos: ComNome[], presente: boolean): Marcacoes {
  return Object.fromEntries(alunos.map((aluno) => [aluno.id, presente]));
}

/** Presentes entre os alunos da lista; marcação de quem não está na lista não conta. */
export function contarPresentes(alunos: ComNome[], marcacoes: Marcacoes): number {
  return alunos.filter((aluno) => marcacoes[aluno.id] === true).length;
}
