// Chamadas abertas neste navegador: modo, TOTP e sala, para retomar a tela depois de recarregar.
// Quem está presente vem do servidor (sessao.acompanhar), não daqui. O sessao.estado não traz o
// segredo TOTP, então o QR de uma chamada só pode ser retomado no navegador que a abriu.

import type { SessaoAberta } from "./protocolo";

export interface ChamadaGuardada extends SessaoAberta {
  salaId?: string;
}

const PREFIXO = "fa.professor.chamada.";
const DA_TURMA = "fa.professor.chamada-da-turma.";
const ULTIMA_SALA = "fa.professor.ultima-sala";

// Janela privada ou dados do site bloqueados: o acesso lança, e a tela segue sem guardar nada
function armazenamentoPadrao(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

function ler(chave: string, armazenamento: Storage | null): string | null {
  try {
    return armazenamento?.getItem(chave) ?? null;
  } catch {
    return null;
  }
}

function gravar(chave: string, valor: string, armazenamento: Storage | null): void {
  try {
    armazenamento?.setItem(chave, valor);
  } catch {
    // Sem armazenamento a chamada continua; só não dá para retomá-la depois de recarregar
  }
}

function remover(chave: string, armazenamento: Storage | null): void {
  try {
    armazenamento?.removeItem(chave);
  } catch {
    // Idem
  }
}

export function guardarChamada(chamada: ChamadaGuardada, armazenamento = armazenamentoPadrao()): void {
  gravar(PREFIXO + chamada.sessaoId, JSON.stringify(chamada), armazenamento);
  gravar(DA_TURMA + chamada.turmaId, chamada.sessaoId, armazenamento);
}

export function lerChamada(sessaoId: string, armazenamento = armazenamentoPadrao()): ChamadaGuardada | null {
  const bruto = ler(PREFIXO + sessaoId, armazenamento);
  if (!bruto) return null;
  try {
    const chamada = JSON.parse(bruto) as ChamadaGuardada;
    return chamada.sessaoId === sessaoId ? chamada : null;
  } catch {
    return null;
  }
}

/** Última chamada aberta da turma neste navegador, se ainda estiver guardada. */
export function chamadaDaTurma(turmaId: string, armazenamento = armazenamentoPadrao()): ChamadaGuardada | null {
  const sessaoId = ler(DA_TURMA + turmaId, armazenamento);
  return sessaoId ? lerChamada(sessaoId, armazenamento) : null;
}

export function esquecerChamada(sessaoId: string, armazenamento = armazenamentoPadrao()): void {
  const chamada = lerChamada(sessaoId, armazenamento);
  remover(PREFIXO + sessaoId, armazenamento);
  if (chamada && ler(DA_TURMA + chamada.turmaId, armazenamento) === sessaoId) {
    remover(DA_TURMA + chamada.turmaId, armazenamento);
  }
}

export function ultimaSala(armazenamento = armazenamentoPadrao()): string | null {
  return ler(ULTIMA_SALA, armazenamento);
}

export function guardarUltimaSala(salaId: string, armazenamento = armazenamentoPadrao()): void {
  gravar(ULTIMA_SALA, salaId, armazenamento);
}
