import { afterEach, describe, expect, it, vi } from "vitest";
import fixture from "./fixtures/qr-professor.json";
import {
  criarDecodificador,
  decodificarPixels,
  iniciarLeituraQr,
  type ConstrutorDetector,
} from "./leitorQr";
import { interpretarQr } from "./checkin";

const sessaoId = "66f1c2aa0011223344556677";
const conteudoQr = (id: string, token: string) => `FA1:${id}:${token}`;
function imagemDoProfessor() {
  // Matriz exportada do gerador real; o app do aluno continua independente no Docker.
  const texto = fixture.texto;
  const qr = {
    tamanho: fixture.linhas.length,
    escuro: (linha: number, coluna: number) =>
      fixture.linhas[linha][coluna] === "1",
  };
  const escala = 6;
  const largura = (qr.tamanho + 8) * escala;
  const dados = new Uint8ClampedArray(largura * largura * 4).fill(255);
  for (let y = 0; y < largura; y++)
    for (let x = 0; x < largura; x++) {
      const linha = Math.floor(y / escala) - 4;
      const coluna = Math.floor(x / escala) - 4;
      if (
        linha >= 0 &&
        coluna >= 0 &&
        linha < qr.tamanho &&
        coluna < qr.tamanho &&
        qr.escuro(linha, coluna)
      ) {
        const indice = (y * largura + x) * 4;
        dados[indice] = dados[indice + 1] = dados[indice + 2] = 0;
      }
    }
  const canvas = {
    width: largura,
    height: largura,
    getContext: () => ({
      getImageData: () => ({ data: dados, width: largura, height: largura }),
    }),
  } as unknown as HTMLCanvasElement;
  return { texto, largura, dados, canvas };
}
afterEach(() => vi.unstubAllGlobals());

describe("leitura do QR existente do professor", () => {
  it("decodifica sua matriz real com jsQR e preserva zeros à esquerda", async () => {
    const { dados, largura, texto } = imagemDoProfessor();
    expect(await decodificarPixels(dados, largura, largura)).toBe(texto);
    expect(interpretarQr(texto, sessaoId)).toEqual({
      sessaoId,
      token: "004239",
    });
  });
  it("recusa versões, URLs, tokens inválidos e códigos de outras chamadas", () => {
    for (const texto of [
      "FA2:" + sessaoId + ":123456",
      "https://example.com",
      "FA1:" + sessaoId + ":12345",
      "FA1:" + sessaoId + ":123456\n",
      "FA1:invalido:123456",
    ]) {
      expect(() => interpretarQr(texto, sessaoId)).toThrow();
    }
    expect(() =>
      interpretarQr(conteudoQr(sessaoId, "123456"), "000000000000000000000000"),
    ).toThrow(/outra chamada/);
  });
  it("usa BarcodeDetector com qr_code quando suportado", async () => {
    const detectar = vi.fn().mockResolvedValue([{ rawValue: "nativo" }]);
    class Nativo {
      static getSupportedFormats = async () => ["qr_code"];
      constructor(readonly opcoes: { formats: string[] }) {
        expect(opcoes.formats).toEqual(["qr_code"]);
      }
      detect = detectar;
    }
    const canvas = imagemDoProfessor().canvas;
    expect(await (await criarDecodificador(Nativo))(canvas)).toBe("nativo");
    expect(detectar).toHaveBeenCalledWith(canvas);
  });
  it("usa fallback se BarcodeDetector não existe, não suporta QR ou falha", async () => {
    const { canvas, texto } = imagemDoProfessor();
    for (const construtor of [
      undefined,
      class {
        static getSupportedFormats = async () => ["ean_13"];
      },
      class {
        static getSupportedFormats = async () => ["qr_code"];
        detect() {
          throw new Error("Falha nativa");
        }
      },
      class {
        constructor() {
          throw new Error("Indisponível");
        }
      },
    ]) {
      expect(
        await (
          await criarDecodificador(construtor as ConstrutorDetector | undefined)
        )(canvas),
      ).toBe(texto);
    }
  });
  it("libera câmera autorizada após o cancelamento enquanto a permissão estava pendente", async () => {
    const parar = vi.fn();
    let resolver!: (stream: MediaStream) => void;
    vi.stubGlobal("isSecureContext", true);
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: () =>
          new Promise<MediaStream>((resolve) => {
            resolver = resolve;
          }),
      },
    });
    const controle = new AbortController();
    const video = {
      srcObject: null,
      play: vi.fn(),
    } as unknown as HTMLVideoElement;
    const iniciado = iniciarLeituraQr(video, controle.signal, vi.fn(), vi.fn());
    controle.abort();
    resolver({ getTracks: () => [{ stop: parar }] } as unknown as MediaStream);
    await iniciado;
    expect(parar).toHaveBeenCalledOnce();
    expect(video.play).not.toHaveBeenCalled();
  });
});
