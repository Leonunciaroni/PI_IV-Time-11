import type { ReactNode } from "react";

/** Região de alerta com altura mínima fixa, para o formulário não pular quando o erro aparece. */
export function MensagemDeErro({ children }: { children?: ReactNode }) {
  return (
    <p role="alert" aria-live="polite" className="min-h-5 text-sm font-medium text-perigo">
      {children}
    </p>
  );
}
