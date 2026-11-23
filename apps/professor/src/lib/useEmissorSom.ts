/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { EmissorToken, RAMPA_SEGUNDOS, VOLUME_REFERENCIA } from "./audio/emissor";
import { criarModem } from "./audio/modem";
import type { ConfiguracaoTotp } from "./protocolo";
import { ErroTotp, codigoTotp, parametrosDaConfiguracao } from "./totp";

export type SituacaoDoSom = "aguardando" | "emitindo" | "pausado";

/** Mensagem em português para o que pode dar errado ao tocar o som. */
export function mensagemDoErroDeSom(erro: unknown): string {
  if (erro instanceof ErroTotp) return erro.message;
  if (erro instanceof DOMException && erro.name === "NotAllowedError") {
    return "O navegador bloqueou o som. Toque em Iniciar som de novo.";
  }
  if (erro instanceof RangeError) return "O sinal não coube na janela do código. Recarregue a página.";
  return "Não foi possível tocar o som neste navegador.";
}

/**
 * Emissão do token da chamada pelo alto-falante (P8, #44): o EmissorToken toca um quadro do modem
 * FSK a cada janela TOTP. O AudioContext só nasce no clique de iniciar, que é o toque que o
 * navegador exige para liberar o áudio.
 */
export function useEmissorSom(config: ConfiguracaoTotp, diferencaRelogioMs: () => number) {
  const [situacao, setSituacao] = useState<SituacaoDoSom>("aguardando");
  const [volume, setVolume] = useState(VOLUME_REFERENCIA);
  const [erro, setErro] = useState<string | null>(null);
  const emissor = useRef<EmissorToken<AudioContext> | null>(null);
  // Os campos, e não o objeto: a configuração lida do navegador é um objeto novo a cada renderização
  const { segredo, algoritmo, digitos, periodoSegundos, t0 } = config;

  // Troca de configuração ou saída da tela: para o som e libera o áudio depois do fade out
  useEffect(
    () => () => {
      const atual = emissor.current;
      emissor.current = null;
      if (!atual) return;
      atual.parar();
      setTimeout(() => void atual.contexto.close().catch(() => {}), RAMPA_SEGUNDOS * 2000);
    },
    [segredo, algoritmo, digitos, periodoSegundos, t0],
  );

  const iniciar = useCallback(async () => {
    setErro(null);
    try {
      if (!emissor.current) {
        const parametros = parametrosDaConfiguracao({ segredo, algoritmo, digitos, periodoSegundos, t0 });
        const modem = criarModem();
        emissor.current = new EmissorToken({
          contexto: new AudioContext(),
          periodoSegundos,
          t0,
          // Relógio do servidor: é ele que valida a janela do token no check-in
          agora: () => Date.now() + diferencaRelogioMs(),
          gerarToken: (instante) => codigoTotp(parametros, instante),
          codificar: (token, contexto) => modem.codificar(token, contexto),
          volume,
          aoErro: (e) => {
            setErro(mensagemDoErroDeSom(e));
            setSituacao("pausado");
          },
        });
      }
      setSituacao("emitindo");
      await emissor.current.iniciar();
    } catch (e) {
      setErro(mensagemDoErroDeSom(e));
      setSituacao("pausado");
    }
  }, [segredo, algoritmo, digitos, periodoSegundos, t0, diferencaRelogioMs, volume]);

  const pausar = useCallback(() => {
    emissor.current?.parar();
    setSituacao("pausado");
  }, []);

  /** De 0 a 1; vale a partir do próximo quadro, sem cortar o que está tocando. */
  const mudarVolume = useCallback((valor: number) => {
    const limitado = Math.min(1, Math.max(0, valor));
    setVolume(limitado);
    if (emissor.current) emissor.current.volume = limitado;
  }, []);

  return { situacao, volume, erro, iniciar, pausar, mudarVolume };
}
