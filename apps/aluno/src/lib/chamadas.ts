import type { ModoChamada, TurmaDoAluno } from "./tipos";

export const nomesModos: Record<ModoChamada, string> = {
  CLASSICA: "Chamada clássica",
  QRCODE: "QR Code",
  SOM: "Onda sonora",
};

export function caminhoCheckin(turma: TurmaDoAluno): string | null {
  const chamada = turma.chamadaAberta;
  if (!chamada || chamada.modo === "CLASSICA") return null;
  const parametros = new URLSearchParams({
    turmaId: turma.id,
    sessaoId: chamada.id,
    modo: chamada.modo,
  });
  return `/checkin?${parametros.toString()}`;
}
