import { useEffect, useState } from "react";
import type { PresencaAtualizada } from "./protocolo";
import type { ClienteSessao } from "./ws";

/**
 * Presenças registradas na sessão, pelos eventos presenca.atualizada (seção 5.2), da mais recente
 * para a mais antiga. O servidor só envia esses eventos depois de sessao.acompanhar (#30), que
 * ainda não existe; até lá a lista fica vazia.
 */
export function usePresencasAoVivo(cliente: ClienteSessao, sessaoId: string): PresencaAtualizada[] {
  const [presencas, setPresencas] = useState<PresencaAtualizada[]>([]);

  useEffect(
    () =>
      cliente.aoEvento<PresencaAtualizada>("presenca.atualizada", (presenca) => {
        if (presenca.sessaoId !== sessaoId) return;
        setPresencas((atuais) =>
          [presenca, ...atuais.filter((p) => p.alunoId !== presenca.alunoId)].filter((p) => p.presente),
        );
      }),
    [cliente, sessaoId],
  );

  return presencas;
}
