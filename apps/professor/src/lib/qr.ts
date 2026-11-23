/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { create } from "qrcode";

export interface DesenhoQr {
  /** Módulos por lado. */
  tamanho: number;
  escuro: (linha: number, coluna: number) => boolean;
  /** Módulos escuros como um caminho SVG de quadrados 1×1, para um viewBox de `tamanho`. */
  caminho: string;
}

/** Matriz do QR Code e o caminho SVG para desenhá-la; o app desenha o SVG, sem canvas. */
export function desenharQr(conteudo: string): DesenhoQr {
  const { modules } = create(conteudo, { errorCorrectionLevel: "M" });
  const tamanho = modules.size;
  const escuro = (linha: number, coluna: number) => modules.get(linha, coluna) === 1;
  let caminho = "";
  for (let linha = 0; linha < tamanho; linha++) {
    for (let coluna = 0; coluna < tamanho; coluna++) {
      if (escuro(linha, coluna)) caminho += `M${coluna} ${linha}h1v1h-1z`;
    }
  }
  return { tamanho, escuro, caminho };
}
