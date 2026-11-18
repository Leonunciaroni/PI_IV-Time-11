import { ErroCheckin } from "./checkin";

type Detector = {
  detect(imagem: HTMLCanvasElement): Promise<{ rawValue: string }[]>;
};
export type ConstrutorDetector = {
  new (opcoes: { formats: string[] }): Detector;
  getSupportedFormats?(): Promise<string[]>;
};

/** O fallback é carregado somente quando a detecção nativa não atende QR Code. */
export async function decodificarPixels(
  dados: Uint8ClampedArray,
  largura: number,
  altura: number,
): Promise<string | null> {
  const { default: jsQR } = await import("jsqr");
  return (
    jsQR(dados, largura, altura, { inversionAttempts: "attemptBoth" })?.data ??
    null
  );
}

export async function criarDecodificador(
  construtor = (
    globalThis as unknown as { BarcodeDetector?: ConstrutorDetector }
  ).BarcodeDetector,
) {
  let detector: Detector | null = null;
  try {
    if (
      construtor &&
      (!construtor.getSupportedFormats ||
        (await construtor.getSupportedFormats()).includes("qr_code"))
    ) {
      detector = new construtor({ formats: ["qr_code"] });
    }
  } catch {
    /* Falha de inicialização nativa: usa o fallback. */
  }
  return async (canvas: HTMLCanvasElement): Promise<string | null> => {
    if (detector) {
      try {
        return (await detector.detect(canvas))[0]?.rawValue ?? null;
      } catch {
        detector = null;
      }
    }
    const contexto = canvas.getContext("2d", { willReadFrequently: true });
    if (!contexto)
      throw new ErroCheckin(
        "CAMERA_INDISPONIVEL",
        "Não foi possível processar a imagem da câmera neste navegador.",
      );
    const imagem = contexto.getImageData(0, 0, canvas.width, canvas.height);
    return decodificarPixels(imagem.data, imagem.width, imagem.height);
  };
}

export function mensagemDaCamera(erro: unknown): ErroCheckin {
  const nome = erro instanceof Error ? erro.name : "";
  const mensagem =
    nome === "NotAllowedError" || nome === "SecurityError"
      ? "Permita o acesso à câmera nas configurações deste site e tente novamente."
      : nome === "NotFoundError" || nome === "OverconstrainedError"
        ? "Nenhuma câmera disponível foi encontrada. Use um aparelho com câmera."
        : nome === "NotReadableError"
          ? "A câmera está sendo usada por outro aplicativo. Feche-o e tente novamente."
          : "Não foi possível iniciar a câmera. Confira as permissões e tente novamente.";
  return erro instanceof ErroCheckin
    ? erro
    : new ErroCheckin("CAMERA_INDISPONIVEL", mensagem);
}

/** Lê somente frames locais; para o stream ao ler, cancelar, falhar ou sair da rota. */
export async function iniciarLeituraQr(
  video: HTMLVideoElement,
  signal: AbortSignal,
  aoLer: (texto: string) => void,
  aoErro: (erro: Error) => void,
): Promise<void> {
  if (!globalThis.isSecureContext)
    throw new ErroCheckin(
      "CONTEXTO_INSEGURO",
      "A câmera exige HTTPS. No computador, localhost também é permitido.",
    );
  if (!navigator.mediaDevices?.getUserMedia)
    throw new ErroCheckin(
      "CAMERA_INDISPONIVEL",
      "A câmera não está disponível neste navegador. Use HTTPS e um navegador compatível.",
    );
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 } },
  });
  let timer: ReturnType<typeof setTimeout> | undefined;
  let parado = false;
  function parar() {
    if (parado) return;
    parado = true;
    if (timer) clearTimeout(timer);
    stream.getTracks().forEach((faixa) => faixa.stop());
    video.srcObject = null;
    signal.removeEventListener("abort", parar);
  }
  if (signal.aborted) {
    parar();
    return;
  }
  signal.addEventListener("abort", parar, { once: true });
  video.srcObject = stream;
  try {
    await video.play();
    const decodificar = await criarDecodificador();
    const canvas = document.createElement("canvas");
    const contexto = canvas.getContext("2d", { willReadFrequently: true });
    if (!contexto)
      throw new ErroCheckin(
        "CAMERA_INDISPONIVEL",
        "Este navegador não consegue processar a câmera.",
      );
    async function lerFrame() {
      if (parado || signal.aborted) return;
      try {
        if (video.readyState >= 2 && video.videoWidth && video.videoHeight) {
          const escala = Math.min(1, 960 / video.videoWidth);
          canvas.width = Math.max(1, Math.round(video.videoWidth * escala));
          canvas.height = Math.max(1, Math.round(video.videoHeight * escala));
          contexto!.drawImage(video, 0, 0, canvas.width, canvas.height);
          const texto = await decodificar(canvas);
          if (parado || signal.aborted) return;
          if (texto) {
            parar();
            aoLer(texto);
            return;
          }
        }
        timer = setTimeout(lerFrame, 200);
      } catch (erro) {
        parar();
        if (!signal.aborted) aoErro(mensagemDaCamera(erro));
      }
    }
    if (!parado && !signal.aborted) void lerFrame();
  } catch (erro) {
    parar();
    if (!signal.aborted) throw mensagemDaCamera(erro);
  }
}
