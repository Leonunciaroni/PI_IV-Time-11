// Lógica da lista de presença, sem interface: a clássica (P6) e a lista ao vivo (P7 e contador).

import type { OrigemPresenca, PresencaAtualizada, SessaoEstado } from "./protocolo";

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

// Lista de presentes ao vivo, juntando sessao.estado e presenca.atualizada (seção 4.5)

/** Presença como a tela mostra, com o nome normalizado (`nome` no estado, `nomeAluno` no evento). */
export interface PresenteAoVivo {
  alunoId: string;
  nome: string;
  origem: OrigemPresenca;
  registradoEm: number;
}

interface RegistroAoVivo extends PresenteAoVivo {
  presente: boolean;
}

export interface PresencasAoVivo {
  /** Último registro de cada aluno, inclusive desmarcados, para um registro antigo não voltar. */
  registros: Record<string, RegistroAoVivo>;
  /**
   * Eventos recebidos desde o último sessao.acompanhar enviado; null quando não há pedido pendente.
   * O servidor inscreve antes de ler o estado, então eles entram de novo quando o estado chega.
   */
  desdeOPedido: RegistroAoVivo[] | null;
  /** Já chegou ao menos um sessao.estado. */
  carregado: boolean;
  totalMatriculados: number | null;
  /** Totais do último evento depois do último estado; sem evento, valem a lista e o estado. */
  totaisDoEvento: { presentes: number; matriculados: number } | null;
}

export type EntradaAoVivo =
  /** Um sessao.acompanhar acabou de ser enviado (primeira vez, reconexão ou recarga). */
  | { tipo: "pedido" }
  | { tipo: "estado"; estado: SessaoEstado }
  /** Evento presenca.atualizada ou resposta de presenca.marcar, que tem os mesmos campos. */
  | { tipo: "evento"; evento: PresencaAtualizada };

export const presencasAoVivoVazias: PresencasAoVivo = {
  registros: {},
  desdeOPedido: null,
  carregado: false,
  totalMatriculados: null,
  totaisDoEvento: null,
};

// O registradoEm vem sempre do relógio do servidor, então decide qual registro é o mais novo;
// no empate vale o que chegou depois
function maisRecente(atual: RegistroAoVivo | undefined, novo: RegistroAoVivo): RegistroAoVivo {
  return atual && atual.registradoEm > novo.registradoEm ? atual : novo;
}

function doEvento(evento: PresencaAtualizada): RegistroAoVivo {
  const { alunoId, nomeAluno, origem, registradoEm, presente } = evento;
  return { alunoId, nome: nomeAluno, origem, registradoEm, presente };
}

/**
 * Junta o estado e os eventos numa só lista, deduplicando por alunoId. Funciona com o evento
 * chegando antes ou depois do estado. Um estado novo (reconexão) substitui os registros antigos:
 * o que aconteceu com a conexão fora não chega como evento, só pelo estado.
 */
export function juntarPresencasAoVivo(atual: PresencasAoVivo, entrada: EntradaAoVivo): PresencasAoVivo {
  switch (entrada.tipo) {
    case "pedido":
      return { ...atual, desdeOPedido: [] };
    case "evento": {
      const registro = doEvento(entrada.evento);
      return {
        ...atual,
        registros: { ...atual.registros, [registro.alunoId]: maisRecente(atual.registros[registro.alunoId], registro) },
        desdeOPedido: atual.desdeOPedido && [...atual.desdeOPedido, registro],
        totaisDoEvento: { presentes: entrada.evento.totalPresentes, matriculados: entrada.evento.totalMatriculados },
      };
    }
    case "estado": {
      const registros: Record<string, RegistroAoVivo> = {};
      for (const presente of entrada.estado.presentes) registros[presente.alunoId] = { ...presente, presente: true };
      // Uma presença gravada entre a inscrição e a leitura vem nas duas fontes; a mais nova vale
      for (const registro of atual.desdeOPedido ?? []) {
        registros[registro.alunoId] = maisRecente(registros[registro.alunoId], registro);
      }
      return {
        registros,
        desdeOPedido: null,
        carregado: true,
        totalMatriculados: entrada.estado.totalMatriculados,
        totaisDoEvento: null,
      };
    }
  }
}

/** Presentes, do registro mais recente para o mais antigo. */
export function listarPresentes(presencas: PresencasAoVivo): PresenteAoVivo[] {
  return Object.values(presencas.registros)
    .filter((registro) => registro.presente)
    .sort((a, b) => b.registradoEm - a.registradoEm)
    .map(({ alunoId, nome, origem, registradoEm }) => ({ alunoId, nome, origem, registradoEm }));
}

export function totaisAoVivo(presencas: PresencasAoVivo): { presentes: number; matriculados: number | null } {
  if (presencas.totaisDoEvento) return presencas.totaisDoEvento;
  return { presentes: listarPresentes(presencas).length, matriculados: presencas.totalMatriculados };
}

// Marcações da chamada clássica: o servidor é a fonte da verdade, com os toques ainda sem
// resposta por cima, para a lista mudar na hora

/** Toque (ou lote) ainda sem resposta; o id separa toques seguidos no mesmo aluno. */
export interface Pendente {
  presente: boolean;
  id: number;
}

export type Pendentes = Record<string, Pendente>;

export function marcacoesExibidas(presentes: { alunoId: string }[], pendentes: Pendentes): Marcacoes {
  const marcacoes: Marcacoes = Object.fromEntries(presentes.map((p) => [p.alunoId, true]));
  for (const [alunoId, pendente] of Object.entries(pendentes)) marcacoes[alunoId] = pendente.presente;
  return marcacoes;
}

/** Tira os pendentes de um toque (ou lote); um toque mais novo no mesmo aluno continua valendo. */
export function soltarPendentes(pendentes: Pendentes, id: number): Pendentes {
  return Object.fromEntries(Object.entries(pendentes).filter(([, pendente]) => pendente.id !== id));
}
