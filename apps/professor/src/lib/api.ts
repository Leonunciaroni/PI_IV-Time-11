import type { Problema, TokenResponse, Usuario } from "./tipos";

/** Erro da API com a mensagem já pronta para exibir ao usuário. */
export class ErroApi extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly campos?: Record<string, string>,
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
    const mensagem = resposta.status >= 500
      ? "O serviço está temporariamente indisponível. Tente novamente em alguns instantes."
      : "Não foi possível concluir a solicitação. Tente novamente.";
    throw new ErroApi(problema?.detail ?? mensagem, resposta.status, problema?.campos);
  }
  return (await resposta.json()) as T;
}

export function login(email: string, senha: string): Promise<TokenResponse> {
  return postar<TokenResponse>("/api/auth/login", { email, senha });
}

export interface DadosCadastro {
  nome: string;
  email: string;
  matricula: string;
  senha: string;
}

/** O papel pertence ao app; a autorização do professor continua sendo feita pela API. */
export function registrar(dados: DadosCadastro): Promise<Usuario> {
  return postar<Usuario>("/api/auth/registro", { ...dados, papel: "PROFESSOR" });
}
