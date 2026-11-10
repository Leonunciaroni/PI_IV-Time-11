import { useEffect, useState, useSyncExternalStore } from "react";
import { encerrarSessao, lerSessao } from "./sessao";
import { ClienteSessao, urlDaSessao, type EstadoConexao } from "./ws";

/**
 * Conexão com o servidor de sessão enquanto a tela está visível. Com cacheComponents a rota fica
 * preservada ao navegar, mas os efeitos são limpos: a conexão fecha ao sair e reabre ao voltar.
 */
export function useClienteSessao(): { cliente: ClienteSessao; estado: EstadoConexao } {
  const [cliente] = useState(
    () => new ClienteSessao({ url: () => urlDaSessao(), token: () => lerSessao()?.token ?? null }),
  );
  const estado = useSyncExternalStore(
    (ouvinte) => cliente.aoMudarEstado(ouvinte),
    () => cliente.estado,
    () => cliente.estado,
  );

  useEffect(() => {
    cliente.conectar();
    return () => cliente.fechar();
  }, [cliente]);

  // Token recusado pelo servidor: some com a sessão local e o AreaAutenticada leva ao login
  useEffect(() => {
    if (estado === "sessao-expirada") encerrarSessao();
  }, [estado]);

  return { cliente, estado };
}

/** Texto para o estado da conexão, exibido perto do botão que depende dela. */
export function descreverConexao(estado: EstadoConexao): string {
  switch (estado) {
    case "pronto":
      return "Conectado ao servidor de chamada";
    case "reconectando":
      return "Conexão perdida. Tentando reconectar...";
    case "incompativel":
      return "Esta versão do app está desatualizada. Recarregue a página.";
    case "sessao-expirada":
      return "Sua sessão expirou. Entre novamente.";
    default:
      return "Conectando ao servidor de chamada...";
  }
}
