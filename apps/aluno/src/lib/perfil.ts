// Guilherme Preventi Correia
// RA: 25001694

import { encerrarSessao, lerSessao } from "./sessao";
import type { Problema, Usuario } from "./tipos";

export type AlteracaoPerfil =
  | { nome: string; email: string }
  | { senhaAtual: string; novaSenha: string };

export class ErroPerfil extends Error {
  constructor(message: string, readonly campos: Record<string, string> = {}) {
    super(message);
    this.name = "ErroPerfil";
  }
}

export function validarSenha(atual: string, nova: string, confirmacao: string): void {
  const campos: Record<string, string> = {};
  if (!atual) campos.senhaAtual = "Informe sua senha atual.";
  if (nova.length < 8 || nova.length > 72 || !nova.trim())
    campos.novaSenha = "Use de 8 a 72 caracteres, sem preencher apenas espaços.";
  else if (new TextEncoder().encode(nova).length > 72)
    campos.novaSenha = "A senha excede 72 bytes. Use uma senha mais curta.";
  if (nova !== confirmacao) campos.confirmacao = "As senhas não coincidem.";
  if (Object.keys(campos).length) throw new ErroPerfil("Revise os campos indicados.", campos);
}

/** O PATCH devolve Usuario, não um novo JWT. Papel e matrícula não são enviados. */
export async function editarPerfil(
  token: string,
  dados: AlteracaoPerfil,
  signal: AbortSignal,
): Promise<Usuario> {
  let resposta: Response;
  try {
    resposta = await fetch("/api/auth/me", {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify(dados),
      signal,
    });
  } catch (erro) {
    if (signal.aborted) throw erro;
    throw new ErroPerfil("Não foi possível conectar ao servidor. Verifique sua conexão.");
  }
  if (!resposta.ok) {
    if (resposta.status === 401 && lerSessao()?.token === token) encerrarSessao();
    const problema = await resposta.json().catch(() => null) as Problema | null;
    throw new ErroPerfil(
      problema?.detail ?? (resposta.status === 409
        ? "Este e-mail já está em uso. Informe outro e-mail."
        : resposta.status === 401
          ? "Sua sessão expirou. Entre novamente."
          : "Não foi possível salvar as alterações. Tente novamente."),
      problema?.campos,
    );
  }
  return resposta.json() as Promise<Usuario>;
}
