// Guilherme Preventi Correia
// RA: 25001694

import { ErroCheckin } from "../checkin";

export interface ReceptorSom {
  iniciar(): Promise<boolean>;
  parar(): Promise<void>;
}
export interface OpcoesReceptor {
  aoToken(token: string): void;
  aoErro(erro: unknown): void;
}
export interface FabricaReceptor {
  criar(opcoes: OpcoesReceptor): ReceptorSom;
}

/** Módulos originais da #26, publicados junto ao app para preservar os URLs do Worker/worklet. */
export async function carregarReceptor(): Promise<FabricaReceptor> {
  const caminho = "/aluno/audio/receptor.mjs";
  const modulo = await import(/* webpackIgnore: true */ /* turbopackIgnore: true */ caminho) as {
    ReceptorFsk: new (opcoes: OpcoesReceptor) => ReceptorSom;
  };
  return { criar: (opcoes) => new modulo.ReceptorFsk(opcoes) };
}

export function mensagemDoMicrofone(erro: unknown): ErroCheckin {
  if (erro instanceof ErroCheckin) return erro;
  const nome = erro instanceof Error ? erro.name : "";
  if (nome === "NotAllowedError" || nome === "SecurityError")
    return new ErroCheckin("MICROFONE_NEGADO", "Permita o acesso ao microfone nas configurações deste site e tente novamente.");
  if (nome === "NotFoundError")
    return new ErroCheckin("MICROFONE_AUSENTE", "Nenhum microfone foi encontrado. Conecte um microfone ou use outro aparelho.");
  if (nome === "NotReadableError")
    return new ErroCheckin("MICROFONE_OCUPADO", "O microfone está ocupado ou indisponível. Feche outros aplicativos que o utilizam e tente novamente.");
  if (nome === "RangeError")
    return new ErroCheckin("AUDIO_INCOMPATIVEL", "Este aparelho não consegue captar a faixa de áudio. Use um navegador compatível ou peça uma chamada por QR Code ou clássica.");
  return new ErroCheckin("FALHA_AUDIO", "Não foi possível iniciar ou manter a escuta. Tente novamente ou peça uma chamada por QR Code ou clássica.");
}

/** Um gesto inicia a captura; o primeiro token íntegro encerra o microfone antes de sair daqui. */
export function escutarToken(
  fabrica: FabricaReceptor,
  signal: AbortSignal,
  aoEscutar: () => void,
): Promise<string> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new DOMException("Escuta cancelada", "AbortError")); return; }
    if (!globalThis.isSecureContext) {
      reject(new ErroCheckin("CONTEXTO_INSEGURO", "Abra o sistema em HTTPS para usar o microfone. No computador, localhost também é permitido."));
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia || !globalThis.AudioContext || !globalThis.AudioWorkletNode || !globalThis.Worker) {
      reject(new ErroCheckin("AUDIO_INCOMPATIVEL", "Este navegador não oferece a captura de som necessária. Use um navegador atualizado ou peça ao professor QR Code ou chamada clássica."));
      return;
    }
    let finalizado = false;
    let receptor: ReceptorSom | null = null;
    let limite: ReturnType<typeof setTimeout> | undefined;
    const concluir = (erro: unknown, token?: string) => {
      if (finalizado) return;
      finalizado = true;
      clearTimeout(limite);
      signal.removeEventListener("abort", cancelar);
      // parar() libera tracks e Worker imediatamente; a promessa aguarda o contexto fechar.
      try {
        Promise.resolve(receptor?.parar()).then(
          () => erro ? reject(erro) : resolve(token!),
          () => reject(new ErroCheckin("FALHA_AUDIO", "Não foi possível finalizar a captura. Recarregue a página antes de tentar novamente.")),
        );
      } catch (falha) { reject(mensagemDoMicrofone(falha)); }
    };
    const cancelar = () => concluir(new DOMException("Escuta cancelada", "AbortError"));
    signal.addEventListener("abort", cancelar, { once: true });
    limite = setTimeout(() => concluir(new ErroCheckin("MICROFONE_DEMOROU", "A abertura do microfone demorou. Confira a permissão do site e tente novamente.")), 20000);
    try {
      receptor = fabrica.criar({
        aoToken: (token) => {
          if (!/^\d{6}$/.test(token)) return;
          concluir(null, token);
        },
        aoErro: (erro) => concluir(mensagemDoMicrofone(erro)),
      });
      // Antes de qualquer await: o AudioContext é liberado pelo toque do usuário.
      receptor.iniciar().then((iniciado) => {
        if (finalizado) return;
        if (!iniciado) { cancelar(); return; }
        clearTimeout(limite);
        limite = setTimeout(() => concluir(new ErroCheckin("SINAL_NAO_RECEBIDO", "Nenhum sinal válido foi recebido. Aproxime-se do emissor ou peça ao professor QR Code ou chamada clássica.")), 45000);
        aoEscutar();
      }).catch((erro) => concluir(mensagemDoMicrofone(erro)));
    } catch (erro) { concluir(mensagemDoMicrofone(erro)); }
  });
}
