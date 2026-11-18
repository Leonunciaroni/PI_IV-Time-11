import { useEffect, useMemo, useSyncExternalStore } from "react";
import { AcompanhamentoSessao, type SituacaoAoVivo } from "./acompanhamento";
import type { PresencaAtualizada } from "./protocolo";
import type { ClienteSessao } from "./ws";

export interface AoVivo extends SituacaoAoVivo {
  /** Junta a resposta de presenca.marcar na lista, sem esperar o evento. */
  registrar: (presenca: PresencaAtualizada) => void;
  /** Pede o estado de novo; resolve quando ele chega ou o pedido falha. */
  recarregar: () => Promise<void>;
}

/**
 * Acompanha a sessão aberta (sessao.acompanhar, #38): pede o estado a cada conexão pronta,
 * inclusive depois de reconectar, e junta os eventos presenca.atualizada da sessão.
 */
export function useAcompanharSessao(cliente: ClienteSessao, sessaoId: string): AoVivo {
  const acompanhamento = useMemo(() => new AcompanhamentoSessao(cliente, sessaoId), [cliente, sessaoId]);

  useEffect(() => acompanhamento.iniciar(), [acompanhamento]);

  const situacao = useSyncExternalStore(
    acompanhamento.inscrever,
    () => acompanhamento.situacao,
    () => acompanhamento.situacao,
  );

  return useMemo(
    () => ({
      ...situacao,
      registrar: (presenca: PresencaAtualizada) => acompanhamento.registrar(presenca),
      recarregar: () => acompanhamento.recarregar(),
    }),
    [situacao, acompanhamento],
  );
}
