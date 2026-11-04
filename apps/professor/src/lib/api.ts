import { encerrarSessao, lerSessao } from "./sessao";
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
async function requisitar<T>(
  metodo: "GET" | "POST",
  caminho: string,
  corpo?: unknown,
  autenticada = false,
): Promise<T> {
  const cabecalhos: Record<string, string> = {};
  if (corpo !== undefined) cabecalhos["Content-Type"] = "application/json";
  if (autenticada) {
    const sessao = lerSessao();
    if (!sessao) throw new ErroApi("Sua sessão expirou. Entre novamente.", 401);
    cabecalhos.Authorization = `Bearer ${sessao.token}`;
  }

  let resposta: Response;
  try {
    resposta = await fetch(caminho, {
      method: metodo,
      headers: cabecalhos,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
  } catch {
    throw new ErroApi("Não foi possível conectar ao servidor. Verifique sua conexão.", 0);
  }

  if (!resposta.ok) {
    // Token recusado: o AreaAutenticada redireciona para o login quando a sessão some
    if (autenticada && resposta.status === 401) encerrarSessao();
    const problema = (await resposta.json().catch(() => null)) as Problema | null;
    throw new ErroApi(problema?.detail ?? "Erro inesperado. Tente novamente.", resposta.status);
  }
  if (resposta.status === 204) return undefined as T;
  return (await resposta.json()) as T;
}

/** GET com o JWT da sessão atual. */
export function obter<T>(caminho: string): Promise<T> {
  return requisitar<T>("GET", caminho, undefined, true);
}

/** POST com o JWT da sessão atual. */
export function enviar<T>(caminho: string, corpo: unknown): Promise<T> {
  return requisitar<T>("POST", caminho, corpo, true);
}

/** POST sem autenticação (login e cadastro). */
function postar<T>(caminho: string, corpo: unknown): Promise<T> {
  return requisitar<T>("POST", caminho, corpo);
}

export function login(email: string, senha: string): Promise<TokenResponse> {
  return postar<TokenResponse>("/api/auth/login", { email, senha });
}
