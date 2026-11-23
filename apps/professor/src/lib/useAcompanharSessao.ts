/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { useEffect, useMemo, useSyncExternalStore } from "react";
import { AcompanhamentoSessao, type SituacaoAoVivo } from "./acompanhamento";
import type { PresencaAtualizada, SessaoModoAlterado } from "./protocolo";
import type { ClienteSessao } from "./ws";

export interface AoVivo extends SituacaoAoVivo {
  /** Junta a resposta de presenca.marcar na lista, sem esperar o evento. */
  registrar: (presenca: PresencaAtualizada) => void;
  /** Pede o estado de novo; resolve quando ele chega ou o pedido falha. */
  recarregar: () => Promise<void>;
  /** Aplica a resposta de sessao.trocar_modo, sem esperar o evento (#49). */
  registrarTroca: (troca: SessaoModoAlterado) => void;
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
      registrarTroca: (troca: SessaoModoAlterado) => acompanhamento.registrarTroca(troca),
    }),
    [situacao, acompanhamento],
  );
}
