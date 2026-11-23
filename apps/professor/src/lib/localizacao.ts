/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
// Localização atual do navegador, usada para preencher as coordenadas de uma sala.

export interface Posicao {
  latitude: number;
  longitude: number;
  precisaoMetros: number;
}

/** O pedaço da Geolocation API que usamos; nos testes, um GPS falso. */
export type Geolocalizador = Pick<Geolocation, "getCurrentPosition">;

/** Falha ao obter a localização, com a mensagem pronta para exibir. */
export class ErroLocalizacao extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ErroLocalizacao";
  }
}

const MENSAGENS: Record<number, string> = {
  1: "Permita o acesso à localização no navegador para preencher as coordenadas.",
  2: "Não foi possível obter a localização. Tente de novo ou preencha as coordenadas à mão.",
  3: "A localização demorou demais. Tente de novo.",
};

/**
 * Pede a posição atual com alta precisão. O navegador só libera a localização em contexto seguro
 * (HTTPS ou localhost), por isso o padrão vem de `window.isSecureContext`.
 */
export function obterLocalizacao(
  geo: Geolocalizador | undefined = globalThis.navigator?.geolocation,
  seguro: boolean = globalThis.isSecureContext ?? false,
): Promise<Posicao> {
  if (!seguro) return Promise.reject(new ErroLocalizacao("A localização só funciona em HTTPS ou em localhost."));
  if (!geo) return Promise.reject(new ErroLocalizacao("Este navegador não informa a localização."));

  return new Promise((resolver, rejeitar) => {
    geo.getCurrentPosition(
      ({ coords }) =>
        resolver({ latitude: coords.latitude, longitude: coords.longitude, precisaoMetros: coords.accuracy }),
      (erro) => rejeitar(new ErroLocalizacao(MENSAGENS[erro.code] ?? MENSAGENS[2])),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  });
}
