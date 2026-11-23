/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Guilherme Preventi Correia
// RA: 25001694

import type {
  HistoricoAluno,
  Problema,
  TokenResponse,
  TurmaDoAluno,
  SessaoAtiva,
} from "./tipos";

/** Erro da API com a mensagem já pronta para exibir ao usuário. */
export class ErroApi extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ErroApi";
  }
}

// A API é servida na mesma origem pelo Nginx (/api), fora do basePath da aplicação.
async function postar<T>(caminho: string, corpo: unknown): Promise<T> {
  let resposta: Response;
  try {
    resposta = await fetch(caminho, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(corpo),
    });
  } catch {
    throw new ErroApi(
      "Não foi possível conectar ao servidor. Verifique sua conexão.",
      0,
    );
  }

  if (!resposta.ok) {
    const problema = (await resposta
      .json()
      .catch(() => null)) as Problema | null;
    throw new ErroApi(
      problema?.detail ?? "Erro inesperado. Tente novamente.",
      resposta.status,
    );
  }
  return (await resposta.json()) as T;
}

export function login(email: string, senha: string): Promise<TokenResponse> {
  return postar<TokenResponse>("/api/auth/login", { email, senha });
}

async function consultar<T>(
  caminho: string,
  token: string,
  signal?: AbortSignal,
): Promise<T> {
  let resposta: Response;
  try {
    resposta = await fetch(caminho, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
      signal,
    });
  } catch (erro) {
    if (signal?.aborted) throw erro;
    throw new ErroApi(
      "Não foi possível conectar ao servidor. Verifique sua conexão.",
      0,
    );
  }
  if (!resposta.ok) {
    const problema = (await resposta
      .json()
      .catch(() => null)) as Problema | null;
    throw new ErroApi(
      problema?.detail ?? "O serviço está indisponível. Tente novamente.",
      resposta.status,
    );
  }
  return resposta.json() as Promise<T>;
}

export function listarMinhasTurmas(
  token: string,
  signal?: AbortSignal,
): Promise<TurmaDoAluno[]> {
  return consultar("/api/aluno/turmas", token, signal);
}

export function listarSessoesAtivas(token: string, signal?: AbortSignal): Promise<SessaoAtiva[]> {
  return consultar("/api/sessoes/ativas", token, signal);
}

export function consultarHistorico(
  token: string,
  turmaId: string,
  alunoId: string,
  signal?: AbortSignal,
): Promise<HistoricoAluno> {
  return consultar(
    `/api/turmas/${encodeURIComponent(turmaId)}/frequencia/${encodeURIComponent(alunoId)}`,
    token,
    signal,
  );
}
