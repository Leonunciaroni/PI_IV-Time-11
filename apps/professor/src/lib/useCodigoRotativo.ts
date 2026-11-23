/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { useEffect, useState } from "react";
import type { ConfiguracaoTotp } from "./protocolo";
import { ErroTotp, codigoTotp, janelaTotp, msAteProximaJanela, parametrosDaConfiguracao } from "./totp";

const INTERVALO_MS = 250;

/**
 * Código TOTP da janela atual, renovado sozinho. O relógio usado é o do servidor (o do navegador
 * corrigido pela diferença medida no WebSocket), porque é ele que valida a janela no check-in.
 */
export function useCodigoRotativo(config: ConfiguracaoTotp, diferencaRelogioMs: () => number) {
  // Os campos, e não o objeto: a configuração lida do navegador é um objeto novo a cada renderização
  const { segredo, algoritmo, digitos, periodoSegundos, t0 } = config;
  const [agora, setAgora] = useState(() => Date.now() + diferencaRelogioMs());
  const [codigo, setCodigo] = useState<{ janela: number; token: string } | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    const relogio = setInterval(() => setAgora(Date.now() + diferencaRelogioMs()), INTERVALO_MS);
    return () => clearInterval(relogio);
  }, [diferencaRelogioMs]);

  const janela = janelaTotp(agora, periodoSegundos, t0);

  useEffect(() => {
    let cancelado = false;
    const parametros = parametrosDaConfiguracao({ segredo, algoritmo, digitos, periodoSegundos, t0 });
    codigoTotp(parametros, (janela * periodoSegundos + t0) * 1000).then(
      (token) => {
        if (!cancelado) setCodigo({ janela, token });
      },
      (e: unknown) => {
        if (!cancelado) setErro(e instanceof ErroTotp ? e.message : "Não foi possível gerar o código.");
      },
    );
    return () => {
      cancelado = true;
    };
  }, [segredo, algoritmo, digitos, periodoSegundos, t0, janela]);

  const periodoMs = periodoSegundos * 1000;
  return {
    // Enquanto o código novo é calculado (milissegundos), o anterior continua válido no servidor
    token: codigo?.token ?? null,
    restanteMs: msAteProximaJanela(agora, periodoSegundos, t0),
    periodoMs,
    erro,
  };
}
