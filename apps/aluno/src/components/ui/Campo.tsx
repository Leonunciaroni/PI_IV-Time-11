/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { useId, type InputHTMLAttributes } from "react";

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  rotulo: string;
  /** Orientação curta exibida abaixo do campo, como o formato esperado. */
  dica?: string;
  /** Mensagem de validação, exibida logo abaixo do campo. */
  erro?: string;
}

/**
 * Campo de texto com rótulo, no mesmo formato dos formulários do app do professor. O foco usa o
 * roxo da logo do aluno (text-marca), como o professor usa o azul da logo dele. As cores vêm das
 * variáveis de globals.css, que já trazem o par do tema escuro.
 */
export function Campo({ rotulo, dica, erro, ...entrada }: CampoProps) {
  const id = useId();
  // O leitor de tela anuncia a dica e o erro junto com o rótulo ao focar o campo
  const descricao = [dica && `${id}-dica`, erro && `${id}-erro`].filter(Boolean).join(" ") || undefined;

  return (
    <label className="flex flex-col gap-1 text-sm font-medium">
      {rotulo}
      <input
        {...entrada}
        aria-invalid={erro !== undefined}
        aria-describedby={descricao}
        className={`rounded-lg border bg-superficie px-3 py-2 text-base outline-none focus:border-marca focus:ring-1 focus:ring-marca ${
          erro ? "border-red-600 dark:border-red-400" : "border-borda-forte"
        }`}
      />
      {dica && (
        <span id={`${id}-dica`} className="text-sm font-normal text-suave">
          {dica}
        </span>
      )}
      {erro && (
        <span id={`${id}-erro`} role="alert" className="text-sm font-medium text-red-600 dark:text-red-400">
          {erro}
        </span>
      )}
    </label>
  );
}
