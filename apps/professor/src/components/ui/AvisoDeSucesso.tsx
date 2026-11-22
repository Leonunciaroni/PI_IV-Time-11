import type { ReactNode } from "react";

/** Confirmação de uma ação que deu certo, no topo da tela para onde o usuário foi levado. */
export function AvisoDeSucesso({ children }: { children: ReactNode }) {
  return (
    <p role="status" className="rounded-controle border border-sucesso-borda bg-sucesso-fundo px-4 py-3 text-sm text-sucesso-texto">
      {children}
    </p>
  );
}
