import type { Problema, TokenResponse } from "./tipos";

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
    throw new ErroApi("Não foi possível conectar ao servidor. Verifique sua conexão.", 0);
  }

  if (!resposta.ok) {
    const problema = (await resposta.json().catch(() => null)) as Problema | null;
    throw new ErroApi(problema?.detail ?? "Erro inesperado. Tente novamente.", resposta.status);
  }
  return (await resposta.json()) as T;
}

export function login(email: string, senha: string): Promise<TokenResponse> {
  return postar<TokenResponse>("/api/auth/login", { email, senha });
}
