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
