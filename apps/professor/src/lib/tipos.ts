// Tipos da API REST (docs/api-rest.md).

import type { ModoChamada } from "./protocolo";

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

/** Uma aula encerrada no dashboard (GET /api/turmas/{id}/estatisticas). */
export interface AulaEstatistica {
  sessaoId: string;
  /** ISO-8601, abertura da aula */
  data: string;
  modo: ModoChamada;
  presentes: number;
  matriculados: number;
  percentual: number;
}

/** Frequência acumulada de um aluno nas aulas encerradas da turma. */
export interface AlunoEstatistica {
  alunoId: string;
  nome: string;
  matricula: string;
  presencas: number;
  aulas: number;
  percentual: number;
}

/** Resposta de GET /api/turmas/{id}/estatisticas (tela P9). */
export interface EstatisticasTurma {
  aulasDadas: number;
  totalAlunos: number;
  frequenciaMedia: number;
  minimo: number;
  /** Em ordem cronológica. */
  aulas: AulaEstatistica[];
  /** Do menor para o maior percentual. */
  abaixoDoMinimo: AlunoEstatistica[];
  usoPorModo: Record<ModoChamada, number>;
}

/** Resposta de POST /api/turmas/{id}/importacao (CSV de alunos). */
export interface ResultadoImportacao {
  importados: number;
  jaMatriculados: number;
  /** `linha` conta o cabeçalho como 1. */
  rejeitados: { linha: number; motivo: string }[];
  totalAlunos: number;
}
