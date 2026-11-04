import { describe, expect, it } from "vitest";
import { ErroLocalizacao, obterLocalizacao, type Geolocalizador } from "./localizacao";

/** GPS falso: responde com a posição ou com o código de erro da Geolocation API. */
function gps(resposta: { latitude: number; longitude: number; accuracy: number } | { codigo: 1 | 2 | 3 }): Geolocalizador {
  return {
    getCurrentPosition(sucesso, falha) {
      if ("codigo" in resposta) {
        falha?.({ code: resposta.codigo, message: "", PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 });
      } else {
        sucesso({ coords: resposta, timestamp: 0 } as unknown as GeolocationPosition);
      }
    },
  };
}

async function mensagemDe(promessa: Promise<unknown>): Promise<string> {
  try {
    await promessa;
  } catch (e) {
    if (e instanceof ErroLocalizacao) return e.message;
    throw e;
  }
  throw new Error("era esperado erro de localização");
}

describe("obterLocalizacao", () => {
  it("devolve latitude, longitude e precisão em metros", async () => {
    const posicao = await obterLocalizacao(gps({ latitude: -22.8337, longitude: -47.0525, accuracy: 12.4 }), true);
    expect(posicao).toEqual({ latitude: -22.8337, longitude: -47.0525, precisaoMetros: 12.4 });
  });

  it("explica que precisa de HTTPS ou localhost fora de contexto seguro", async () => {
    const posicao = obterLocalizacao(gps({ latitude: 0, longitude: 0, accuracy: 1 }), false);
    expect(await mensagemDe(posicao)).toBe("A localização só funciona em HTTPS ou em localhost.");
  });

  it("avisa quando o navegador não oferece localização", async () => {
    expect(await mensagemDe(obterLocalizacao(undefined, true))).toBe("Este navegador não informa a localização.");
  });

  it("traduz permissão negada", async () => {
    expect(await mensagemDe(obterLocalizacao(gps({ codigo: 1 }), true))).toBe(
      "Permita o acesso à localização no navegador para preencher as coordenadas.",
    );
  });

  it("traduz posição indisponível", async () => {
    expect(await mensagemDe(obterLocalizacao(gps({ codigo: 2 }), true))).toBe(
      "Não foi possível obter a localização. Tente de novo ou preencha as coordenadas à mão.",
    );
  });

  it("traduz tempo esgotado", async () => {
    expect(await mensagemDe(obterLocalizacao(gps({ codigo: 3 }), true))).toBe(
      "A localização demorou demais. Tente de novo.",
    );
  });
});
