/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
// Horário padrão da turma, no mesmo formato que a API exige (docs/api-rest.md): "Dia HH:MM".

export const MENSAGEM_HORARIO = "Use o formato Dia HH:MM, como Seg 19:00 (de segunda a domingo, hora de 00 a 23).";

// Abreviados ou por extenso, sem acento e em minúsculas (maiúsculas e acentos não importam)
const DIAS = new Set([
  "seg", "segunda",
  "ter", "terca",
  "qua", "quarta",
  "qui", "quinta",
  "sex", "sexta",
  "sab", "sabado",
  "dom", "domingo",
]);

const FORMATO = /^(\p{L}+)[ \t\n\v\f\r]+(\d{2}):(\d{2})$/u;

/** Dia de segunda a domingo, hora de 00 a 23 e minuto de 00 a 59. A API é a fonte final da regra. */
export function horarioValido(texto: string): boolean {
  const partes = FORMATO.exec(texto.trim());
  if (!partes) return false;
  const [, dia, hora, minuto] = partes;
  const diaSemAcento = dia.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
  return DIAS.has(diaSemAcento) && Number(hora) <= 23 && Number(minuto) <= 59;
}
