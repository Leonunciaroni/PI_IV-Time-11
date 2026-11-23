/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
// Gerador TOTP (RFC 6238) do QR Code rotativo, a partir da configuração que o servidor envia em
// sessao.aberta (docs/protocolo-v1.md, seção 5.3). O HMAC vem do Web Crypto do navegador.

import type { ConfiguracaoTotp } from "./protocolo";

const ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/** Nome do algoritmo no protocolo (como no Java) para o hash do Web Crypto. */
const HASHES: Record<string, string> = { HmacSHA1: "SHA-1", HmacSHA256: "SHA-256", HmacSHA512: "SHA-512" };

export interface ParametrosTotp {
  chave: Uint8Array;
  algoritmo: string;
  digitos: number;
  periodoSegundos: number;
  t0: number;
}

/** Falha ao gerar o código, com a mensagem pronta para exibir. */
export class ErroTotp extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ErroTotp";
  }
}

/** Base32 da RFC 4648; ignora maiúsculas, espaços e o preenchimento com "=". */
export function decodificarBase32(texto: string): Uint8Array {
  const limpo = texto.toUpperCase().replace(/[\s=]/g, "");
  const bytes: number[] = [];
  let acumulado = 0;
  let bits = 0;
  for (const caractere of limpo) {
    const valor = ALFABETO_BASE32.indexOf(caractere);
    if (valor < 0) throw new ErroTotp(`Caractere inválido no segredo: ${caractere}`);
    acumulado = (acumulado << 5) | valor;
    bits += 5;
    if (bits >= 8) {
      bytes.push((acumulado >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return new Uint8Array(bytes);
}

export function parametrosDaConfiguracao(config: ConfiguracaoTotp): ParametrosTotp {
  return {
    chave: decodificarBase32(config.segredo),
    algoritmo: config.algoritmo,
    digitos: config.digitos,
    periodoSegundos: config.periodoSegundos,
    t0: config.t0,
  };
}

/** HOTP (RFC 4226): HMAC do contador de 8 bytes e truncamento dinâmico. */
export async function codigoHotp(chave: Uint8Array, contador: number, digitos: number, algoritmo: string): Promise<string> {
  const hash = HASHES[algoritmo];
  if (!hash) throw new ErroTotp(`Algoritmo TOTP não suportado: ${algoritmo}`);
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) throw new ErroTotp("O QR Code só funciona em HTTPS ou em localhost.");

  const mensagem = new ArrayBuffer(8);
  new DataView(mensagem).setBigUint64(0, BigInt(contador));
  const segredo = await subtle.importKey("raw", chave as BufferSource, { name: "HMAC", hash }, false, ["sign"]);
  const hmac = new Uint8Array(await subtle.sign("HMAC", segredo, mensagem));

  const deslocamento = hmac[hmac.length - 1] & 0x0f;
  const binario =
    ((hmac[deslocamento] & 0x7f) << 24) |
    (hmac[deslocamento + 1] << 16) |
    (hmac[deslocamento + 2] << 8) |
    hmac[deslocamento + 3];
  return String(binario % 10 ** digitos).padStart(digitos, "0");
}

/** Janela de tempo T = floor((agora − t0) / período), com t0 e período em segundos. */
export function janelaTotp(agoraMs: number, periodoSegundos: number, t0: number): number {
  return Math.floor((agoraMs / 1000 - t0) / periodoSegundos);
}

/** Milissegundos até o começo da próxima janela, quando o código muda. */
export function msAteProximaJanela(agoraMs: number, periodoSegundos: number, t0: number): number {
  const proxima = (janelaTotp(agoraMs, periodoSegundos, t0) + 1) * periodoSegundos + t0;
  return proxima * 1000 - agoraMs;
}

export function codigoTotp(parametros: ParametrosTotp, agoraMs: number): Promise<string> {
  const janela = janelaTotp(agoraMs, parametros.periodoSegundos, parametros.t0);
  return codigoHotp(parametros.chave, janela, parametros.digitos, parametros.algoritmo);
}

/** Conteúdo do QR Code (seção 6): o prefixo FA1 identifica o formato e a versão. */
export function conteudoQr(sessaoId: string, token: string): string {
  return `FA1:${sessaoId}:${token}`;
}
