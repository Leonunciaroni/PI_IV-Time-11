/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
// Lógica do dashboard de frequência da turma (P9), sem interface.

import type { ModoChamada } from "./protocolo";
import type { AulaEstatistica } from "./tipos";

/** Ordem em que os modos aparecem na tela. */
export const MODOS: readonly ModoChamada[] = ["CLASSICA", "QRCODE", "SOM"];

const ROTULOS: Record<ModoChamada, string> = {
  CLASSICA: "Clássica",
  QRCODE: "QR Code",
  SOM: "Som",
};

export function rotuloDoModo(modo: ModoChamada): string {
  return ROTULOS[modo] ?? modo;
}

const PERCENTUAL = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

/** 87.5 → "87,5%"; 90 → "90%". */
export function formatarPercentual(valor: number): string {
  return `${PERCENTUAL.format(valor)}%`;
}

const DATA_CURTA = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  timeZone: "America/Sao_Paulo",
});

/** Data da aula no horário de Brasília, como "19/10". */
export function dataDaAula(iso: string): string {
  return DATA_CURTA.format(new Date(iso));
}

/** Altura da barra em porcentagem (0 a 100), com um mínimo para a aula de 0% continuar visível. */
export function alturaDaBarra(percentual: number): number {
  const limitado = Math.min(100, Math.max(0, percentual));
  return limitado === 0 ? 2 : limitado;
}

/** A barra fica em destaque quando a aula ficou abaixo do mínimo da turma. */
export function aulaAbaixoDoMinimo(aula: AulaEstatistica, minimo: number): boolean {
  return aula.percentual < minimo;
}

/** Texto lido pelo leitor de tela para cada barra. */
export function descricaoDaAula(aula: AulaEstatistica): string {
  return `${dataDaAula(aula.data)}, ${rotuloDoModo(aula.modo)}: ${aula.presentes} de ${aula.matriculados} presentes (${formatarPercentual(aula.percentual)})`;
}
