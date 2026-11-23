/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
// Salas da validação de distância do modo QR Code (docs/api-rest.md, seção Salas).

export interface Sala {
  id: string;
  nome: string;
  latitude: number;
  longitude: number;
  raioMetros: number;
}

/** Corpo de POST /api/salas. */
export type NovaSala = Omit<Sala, "id">;

/** Campos do formulário, como o professor digitou. */
export type DadosSala = Record<keyof NovaSala, string>;

export type ErrosSala = Partial<Record<keyof DadosSala, string>>;

export type LeituraSala = { ok: true; sala: NovaSala } | { ok: false; erros: ErrosSala };

// Número decimal com ponto ou vírgula (-22.8337 ou -22,8337), sem notação científica
const NUMERO = /^[+-]?\d+([.,]\d+)?$/;

function numero(texto: string): number | null {
  const limpo = texto.trim();
  return NUMERO.test(limpo) ? Number(limpo.replace(",", ".")) : null;
}

/**
 * Valida o formulário com as mesmas regras da API e converte os números. A API continua sendo a
 * fonte final das regras; as mensagens de faixa repetem as dela.
 */
export function lerSala(dados: DadosSala): LeituraSala {
  const erros: ErrosSala = {};
  const nome = dados.nome.trim();
  if (!nome) erros.nome = "Informe o nome da sala.";

  const latitude = numero(dados.latitude);
  if (!dados.latitude.trim()) erros.latitude = "Informe a latitude.";
  else if (latitude === null) erros.latitude = "A latitude deve ser um número, como -22.8337.";
  else if (latitude < -90 || latitude > 90) erros.latitude = "A latitude deve estar entre -90 e 90.";

  const longitude = numero(dados.longitude);
  if (!dados.longitude.trim()) erros.longitude = "Informe a longitude.";
  else if (longitude === null) erros.longitude = "A longitude deve ser um número, como -47.0525.";
  else if (longitude < -180 || longitude > 180) erros.longitude = "A longitude deve estar entre -180 e 180.";

  const raioMetros = numero(dados.raioMetros);
  if (!dados.raioMetros.trim()) erros.raioMetros = "Informe o raio.";
  else if (raioMetros === null) erros.raioMetros = "O raio deve ser um número de metros, como 30.";
  else if (raioMetros <= 0) erros.raioMetros = "O raio deve ser maior que zero.";

  if (Object.keys(erros).length > 0) return { ok: false, erros };
  return { ok: true, sala: { nome, latitude: latitude!, longitude: longitude!, raioMetros: raioMetros! } };
}
