import type { ClienteSessao } from "./ws";
import { ErroSessao } from "./ws";
import type { CheckinOk } from "./protocolo";

export class ErroCheckin extends Error {
  constructor(
    readonly codigo: string,
    mensagem: string,
  ) {
    super(mensagem);
    this.name = "ErroCheckin";
  }
}

export interface ConteudoQr {
  sessaoId: string;
  token: string;
}
export interface Localizacao {
  latitude: number;
  longitude: number;
  precisaoMetros: number;
  obtidaEm: number;
}

/** Formato exato emitido em apps/professor/src/lib/totp.ts:conteudoQr. */
export function interpretarQr(
  texto: string,
  sessaoEsperada: string,
): ConteudoQr {
  const partes = /^FA1:([a-f\d]{24}):(\d{6})$/.exec(texto);
  if (!partes)
    throw new ErroCheckin(
      "QR_INVALIDO",
      "Este QR Code não é uma chamada do Fast Attendance. Aponte para o código exibido pelo professor.",
    );
  if (partes[1] !== sessaoEsperada)
    throw new ErroCheckin(
      "QR_OUTRA_CHAMADA",
      "Este QR Code pertence a outra chamada. Volte ao início ou leia o código desta turma.",
    );
  return { sessaoId: partes[1], token: partes[2] };
}

export function obterLocalizacao(signal: AbortSignal): Promise<Localizacao> {
  if (!globalThis.isSecureContext)
    return Promise.reject(
      new ErroCheckin(
        "CONTEXTO_INSEGURO",
        "Abra o sistema em HTTPS para usar câmera e localização. No computador, localhost também é permitido.",
      ),
    );
  if (!navigator.geolocation)
    return Promise.reject(
      new ErroCheckin(
        "LOCALIZACAO_INDISPONIVEL",
        "Este navegador não oferece localização. Use um navegador compatível ou procure o professor.",
      ),
    );
  return new Promise((resolve, reject) => {
    const cancelar = () =>
      reject(new DOMException("Operação cancelada", "AbortError"));
    if (signal.aborted) {
      cancelar();
      return;
    }
    signal.addEventListener("abort", cancelar, { once: true });
    navigator.geolocation.getCurrentPosition(
      (posicao) => {
        signal.removeEventListener("abort", cancelar);
        if (signal.aborted) return;
        const { latitude, longitude, accuracy } = posicao.coords;
        if (
          !Number.isFinite(latitude) ||
          !Number.isFinite(longitude) ||
          !Number.isFinite(accuracy) ||
          Math.abs(latitude) > 90 ||
          Math.abs(longitude) > 180 ||
          accuracy < 0
        ) {
          reject(
            new ErroCheckin(
              "LOCALIZACAO_INDISPONIVEL",
              "A localização recebida é inválida. Ative o GPS e tente novamente.",
            ),
          );
        } else
          resolve({
            latitude,
            longitude,
            precisaoMetros: accuracy,
            obtidaEm: Date.now(),
          });
      },
      (erro) => {
        signal.removeEventListener("abort", cancelar);
        if (signal.aborted) return;
        const mensagem =
          erro.code === 1
            ? "Permita o acesso à localização nas configurações deste site para confirmar a presença."
            : erro.code === 3
              ? "A localização demorou para responder. Ative o GPS e tente novamente."
              : "Não foi possível obter sua localização. Ative o GPS e tente novamente.";
        reject(new ErroCheckin("LOCALIZACAO_INDISPONIVEL", mensagem));
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 10000 },
    );
  });
}

export async function enviarCheckinQr(
  cliente: ClienteSessao,
  qr: ConteudoQr,
  local: Localizacao,
  dispositivoId: string,
): Promise<CheckinOk> {
  if (cliente.estado !== "pronto")
    throw new ErroCheckin(
      "SEM_CONEXAO",
      "A conexão com a chamada caiu. Aguarde reconectar e leia o QR Code novamente.",
    );
  const resposta = await cliente.requisitar<CheckinOk>("checkin", {
    sessaoId: qr.sessaoId,
    token: qr.token,
    canal: "QRCODE",
    dispositivoId,
    latitude: local.latitude,
    longitude: local.longitude,
    precisaoMetros: local.precisaoMetros,
  });
  if (
    !resposta ||
    resposta.sessaoId !== qr.sessaoId ||
    !Number.isFinite(resposta.registradoEm)
  ) {
    throw new ErroSessao(
      "RESPOSTA_INVALIDA",
      "Não foi possível confirmar a resposta do servidor. Consulte seu histórico antes de tentar novamente.",
    );
  }
  return resposta;
}
