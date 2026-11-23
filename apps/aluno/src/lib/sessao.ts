// Guilherme Preventi Correia
// RA: 25001694

import type { TokenResponse, Usuario } from "./tipos";

// As duas aplicações compartilham a mesma origem; a chave separa a sessão de cada uma.
const CHAVE = "fa.aluno.sessao";

export interface Sessao {
  token: string;
  /** Milissegundos Unix */
  expiraEm: number;
  usuario: Usuario;
}

// Store externo para useSyncExternalStore: com cacheComponents o Next.js preserva o estado das
// rotas visitadas (<Activity>), então a sessão não pode ficar congelada em useState.
const ouvintes = new Set<() => void>();
let ultimoBruto: string | null = null;
let ultimaSessao: Sessao | null = null;

function notificar(): void {
  ouvintes.forEach((ouvinte) => ouvinte());
}

export function assinarSessao(ouvinte: () => void): () => void {
  ouvintes.add(ouvinte);
  // Login ou logout em outra aba
  const aoMudarStorage = (e: StorageEvent) => {
    if (e.key === CHAVE || e.key === null) ouvinte();
  };
  window.addEventListener("storage", aoMudarStorage);
  return () => {
    ouvintes.delete(ouvinte);
    window.removeEventListener("storage", aoMudarStorage);
  };
}

export function salvarSessao(resposta: TokenResponse): void {
  const sessao: Sessao = {
    token: resposta.token,
    expiraEm: Date.parse(resposta.expiraEm),
    usuario: resposta.usuario,
  };
  localStorage.setItem(CHAVE, JSON.stringify(sessao));
  notificar();
}

/**
 * Sessão salva, ou null se não houver ou se o token já tiver expirado. Só no navegador.
 * Devolve a mesma referência enquanto o conteúdo não muda, como exige useSyncExternalStore.
 */
export function lerSessao(): Sessao | null {
  let bruto: string | null;
  try {
    bruto = localStorage.getItem(CHAVE);
  } catch {
    return null;
  }
  if (bruto !== ultimoBruto) {
    ultimoBruto = bruto;
    ultimaSessao = interpretar(bruto);
  }
  if (ultimaSessao && ultimaSessao.expiraEm <= Date.now()) {
    return null;
  }
  return ultimaSessao;
}

function interpretar(bruto: string | null): Sessao | null {
  if (!bruto) return null;
  try {
    const sessao = JSON.parse(bruto) as Sessao;
    return sessao.token && sessao.expiraEm > 0 ? sessao : null;
  } catch {
    return null;
  }
}

export function encerrarSessao(): void {
  localStorage.removeItem(CHAVE);
  notificar();
}

/** Preserva token e validade; uma resposta atrasada não pode recriar outra sessão. */
export function atualizarUsuario(usuario: Usuario, tokenEsperado: string): boolean {
  const atual = lerSessao();
  if (!atual || atual.token !== tokenEsperado || atual.usuario.id !== usuario.id) return false;
  localStorage.setItem(CHAVE, JSON.stringify({ ...atual, usuario }));
  notificar();
  return true;
}
