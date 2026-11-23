/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
// Troca do modo da chamada aberta (#49), sem interface: quais trocas a tela oferece e como a
// chamada guardada no navegador passa a refletir o modo novo.

import type { ChamadaGuardada } from "./chamadas";
import type { ModoChamada, SessaoModoAlterado } from "./protocolo";

/**
 * Trocas oferecidas em cada modo: do som para o QR Code (quando o fundo da sala não capta o sinal) e
 * de qualquer modo para a clássica (contingência final). Na clássica não há troca: o professor já
 * marca a lista à mão.
 */
const TROCAS: Record<ModoChamada, ModoChamada[]> = {
  SOM: ["QRCODE", "CLASSICA"],
  QRCODE: ["CLASSICA"],
  CLASSICA: [],
};

export function trocasPermitidas(modo: ModoChamada): ModoChamada[] {
  return TROCAS[modo];
}

/** Ir para o QR Code pede sala (a do pedido ou a que a sessão já tinha); os demais modos não. */
export function precisaDeSala(modo: ModoChamada): boolean {
  return modo === "QRCODE";
}

/**
 * Chamada guardada com o modo novo. O TOTP e a abertura não mudam: o segredo é o mesmo para QR Code
 * e som. Sem sala no servidor, a sala guardada sai, para a tela não mostrar uma sala que não vale.
 */
export function aplicarModo(chamada: ChamadaGuardada, alteracao: Pick<SessaoModoAlterado, "modo" | "salaId">): ChamadaGuardada {
  const nova: ChamadaGuardada = { ...chamada, modo: alteracao.modo };
  if (alteracao.salaId) nova.salaId = alteracao.salaId;
  else delete nova.salaId;
  return nova;
}

/** A troca recebida (resposta ou evento) é desta chamada e muda algo do que está guardado? */
export function mudaAChamada(chamada: ChamadaGuardada, alteracao: SessaoModoAlterado): boolean {
  return (
    alteracao.sessaoId === chamada.sessaoId &&
    (alteracao.modo !== chamada.modo || (alteracao.salaId ?? undefined) !== chamada.salaId)
  );
}

/**
 * Sala sugerida ao ir para o QR Code: a da chamada (se ela já teve uma), senão a última usada neste
 * navegador, senão a primeira da lista. Só vale uma sala que ainda existe.
 */
export function salaSugerida(idsDasSalas: string[], salaDaChamada: string | undefined, ultimaUsada: string | null): string | null {
  for (const candidata of [salaDaChamada, ultimaUsada]) {
    if (candidata && idsDasSalas.includes(candidata)) return candidata;
  }
  return idsDasSalas[0] ?? null;
}

const NOME: Record<ModoChamada, string> = { CLASSICA: "clássica", QRCODE: "QR Code", SOM: "som" };

/** Texto do botão, como "Trocar para QR Code". */
export function rotuloDaTroca(destino: ModoChamada): string {
  return `Trocar para ${NOME[destino]}`;
}

/** Explicação na confirmação: o que acontece com quem já está presente e com quem falta. */
export function explicacaoDaTroca(destino: ModoChamada): string {
  switch (destino) {
    case "CLASSICA":
      return "Quem já registrou presença continua presente. Os demais você marca na lista.";
    case "QRCODE":
      return "Quem já registrou presença continua presente. Os demais leem o QR Code, de dentro da sala escolhida.";
    case "SOM":
      return "Quem já registrou presença continua presente. Os demais captam o som pelo app.";
  }
}
