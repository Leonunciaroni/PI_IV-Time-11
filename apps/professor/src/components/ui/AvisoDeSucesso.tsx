import type { ReactNode } from "react";

/** Confirmação de uma ação que deu certo, no topo da tela para onde o usuário foi levado. */
export function AvisoDeSucesso({ children }: { children: ReactNode }) {
  return (
    <p role="status" className="rounded-controle border border-sucesso/40 bg-sucesso/10 px-3 py-2 text-sm text-sucesso">
      {children}
    </p>
  );
}
