/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { describe, expect, it } from "vitest";
import { desenharQr } from "./qr";

describe("desenharQr", () => {
  const conteudo = "FA1:66f1c2aa0000000000000000:492039";

  it("gera uma matriz quadrada com os três localizadores nos cantos", () => {
    const { tamanho, escuro } = desenharQr(conteudo);
    expect(tamanho).toBeGreaterThanOrEqual(21);
    // Canto superior esquerdo de cada localizador 7×7 é escuro; o centro do QR não é um localizador
    expect(escuro(0, 0)).toBe(true);
    expect(escuro(0, tamanho - 1)).toBe(true);
    expect(escuro(tamanho - 1, 0)).toBe(true);
    expect(escuro(1, 1)).toBe(false);
  });

  it("descreve os módulos escuros como um caminho SVG de quadrados 1×1", () => {
    const { caminho, tamanho, escuro } = desenharQr(conteudo);
    const quadrados = caminho.match(/M\d+ \d+h1v1h-1z/g) ?? [];
    let escuros = 0;
    for (let linha = 0; linha < tamanho; linha++) for (let coluna = 0; coluna < tamanho; coluna++) if (escuro(linha, coluna)) escuros++;
    expect(quadrados).toHaveLength(escuros);
    expect(caminho.startsWith("M0 0h1v1h-1z")).toBe(true);
  });

  it("muda o desenho quando o código muda", () => {
    expect(desenharQr("FA1:s:111111").caminho).not.toBe(desenharQr("FA1:s:222222").caminho);
  });
});
