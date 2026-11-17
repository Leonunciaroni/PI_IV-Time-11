// Tipos da API REST (docs/api-rest.md).

export type Papel = "PROFESSOR" | "ALUNO";

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

export interface Turma {
  id: string;
  nome: string;
  disciplina: string;
  horarioPadrao: string;
  totalAlunos: number;
  /** ISO-8601 */
  criadaEm: string;
}

/** Dados do aluno exibidos ao professor (sem e-mail). */
export interface Aluno {
  id: string;
  nome: string;
  matricula: string;
}

export interface ResultadoMatricula {
  matriculados: number;
  jaMatriculados: number;
  totalAlunos: number;
}

/** Resposta de POST /api/turmas/{id}/importacao (CSV de alunos). */
export interface ResultadoImportacao {
  importados: number;
  jaMatriculados: number;
  /** `linha` conta o cabeçalho como 1. */
  rejeitados: { linha: number; motivo: string }[];
  totalAlunos: number;
}
