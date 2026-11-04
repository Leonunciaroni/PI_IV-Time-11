import { describe, expect, it } from "vitest";
import { codigoHotp, codigoTotp, conteudoQr, decodificarBase32, janelaTotp, msAteProximaJanela } from "./totp";

const ascii = (texto: string) => new TextEncoder().encode(texto);

describe("decodificarBase32", () => {
  // Vetores da RFC 4648, seção 10 (sem o preenchimento "=", como o servidor envia)
  it.each([
    ["MY", "f"],
    ["MZXQ", "fo"],
    ["MZXW6", "foo"],
    ["MZXW6YQ", "foob"],
    ["MZXW6YTB", "fooba"],
    ["MZXW6YTBOI", "foobar"],
  ])("decodifica %s como %s", (base32, texto) => {
    expect(decodificarBase32(base32)).toEqual(ascii(texto));
  });

  it("aceita minúsculas, espaços e preenchimento", () => {
    expect(decodificarBase32("mzxw 6ytb oi======")).toEqual(ascii("foobar"));
  });

  it("decodifica o segredo de exemplo do protocolo", () => {
    expect([...decodificarBase32("JBSWY3DPEHPK3PXP")]).toEqual([
      0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0xde, 0xad, 0xbe, 0xef,
    ]);
  });

  it("recusa caracteres fora do alfabeto Base32", () => {
    expect(() => decodificarBase32("ABC1")).toThrow();
  });
});

describe("codigoTotp com os vetores da RFC 6238 (Apêndice B)", () => {
  // Semente de 32 bytes do código de referência da RFC para HMAC-SHA256; passo de 30 s; 8 dígitos
  const sha256 = { chave: ascii("12345678901234567890123456789012"), algoritmo: "HmacSHA256", digitos: 8, periodoSegundos: 30, t0: 0 };

  it.each([
    [59, "46119246"],
    [1111111109, "68084774"],
    [1111111111, "67062674"],
    [1234567890, "91819424"],
    [2000000000, "90698825"],
    [20000000000, "77737706"],
  ])("HMAC-SHA256 em t = %i s gera %s", async (segundos, esperado) => {
    expect(await codigoTotp(sha256, segundos * 1000)).toBe(esperado);
  });

  it("aceita também HMAC-SHA1 e HMAC-SHA512, com as sementes da RFC", async () => {
    const sha1 = { ...sha256, chave: ascii("12345678901234567890"), algoritmo: "HmacSHA1" };
    const sha512 = {
      ...sha256,
      chave: ascii("1234567890123456789012345678901234567890123456789012345678901234"),
      algoritmo: "HmacSHA512",
    };
    expect(await codigoTotp(sha1, 59_000)).toBe("94287082");
    expect(await codigoTotp(sha512, 59_000)).toBe("90693936");
  });

  it("com 6 dígitos, como no protocolo, fica com os 6 últimos do código de 8", async () => {
    expect(await codigoTotp({ ...sha256, digitos: 6 }, 59_000)).toBe("119246");
    expect(await codigoTotp({ ...sha256, digitos: 6 }, 1111111109_000)).toBe("084774");
  });

  it("recusa um algoritmo desconhecido", async () => {
    await expect(codigoTotp({ ...sha256, algoritmo: "HmacMD5" }, 59_000)).rejects.toThrow();
  });
});

describe("codigoHotp", () => {
  // RFC 4226, Apêndice D: contadores 0 e 1 com a semente "12345678901234567890" e HMAC-SHA1
  it("confere com os vetores da RFC 4226", async () => {
    const chave = ascii("12345678901234567890");
    expect(await codigoHotp(chave, 0, 6, "HmacSHA1")).toBe("755224");
    expect(await codigoHotp(chave, 1, 6, "HmacSHA1")).toBe("287082");
  });
});

describe("janelas de tempo", () => {
  it("calcula a janela pelo período em segundos e t0", () => {
    expect(janelaTotp(59_000, 30, 0)).toBe(1);
    expect(janelaTotp(14_999, 15, 0)).toBe(0);
    expect(janelaTotp(15_000, 15, 0)).toBe(1);
    expect(janelaTotp(20_000, 15, 5)).toBe(1);
  });

  it("diz quanto falta para o próximo código", () => {
    expect(msAteProximaJanela(15_000, 15, 0)).toBe(15_000);
    expect(msAteProximaJanela(21_000, 15, 0)).toBe(9_000);
    expect(msAteProximaJanela(29_999, 15, 0)).toBe(1);
  });
});

describe("conteudoQr", () => {
  it("segue o formato FA1:<sessaoId>:<token> do protocolo", () => {
    expect(conteudoQr("66f1c2aa", "492039")).toBe("FA1:66f1c2aa:492039");
  });
});
