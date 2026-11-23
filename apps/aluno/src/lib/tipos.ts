// Guilherme Preventi Correia
// RA: 25001694

// Tipos da API REST (docs/api-rest.md).

export type Papel = "PROFESSOR" | "ALUNO";

export type ModoChamada = "CLASSICA" | "QRCODE" | "SOM";

export interface ChamadaAberta {
  id: string;
  modo: ModoChamada;
  abertaEm: string;
}

export interface SessaoAtiva {
  sessaoId: string;
  turmaId: string;
  turma: string;
  disciplina: string;
  modo: ModoChamada;
  abertaEm: string;
}

export interface TurmaDoAluno {
  id: string;
  nome: string;
  disciplina: string;
  horarioPadrao: string;
  aulasDadas: number;
  presencas: number;
  percentual: number;
  minimo: number;
  abaixoDoMinimo: boolean;
  chamadaAberta: ChamadaAberta | null;
}

export interface HistoricoAluno {
  turmaId: string;
  alunoId: string;
  aulasDadas: number;
  presencas: number;
  percentual: number;
  minimo: number;
  abaixoDoMinimo: boolean;
  aulas: {
    sessaoId: string;
    data: string;
    modo: ModoChamada;
    presente: boolean;
  }[];
}

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  papel: Papel;
  matricula: string;
}

export interface TokenResponse {
  token: string;
  tipo: "Bearer";
  /** ISO-8601 */
  expiraEm: string;
  usuario: Usuario;
}

/** Corpo de erro no formato Problem Details (RFC 9457). */
export interface Problema {
  status: number;
  detail?: string;
  campos?: Record<string, string>;
}
