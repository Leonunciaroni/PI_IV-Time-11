import type { InputHTMLAttributes } from "react";

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  rotulo: string;
  /** Mensagem de validação, exibida logo abaixo do campo. */
  erro?: string;
}

/** Campo de texto com rótulo, no estilo dos formulários do app. */
export function Campo({ rotulo, erro, ...entrada }: CampoProps) {
  return (
    <label className="flex flex-col gap-1 text-sm font-medium">
      {rotulo}
      <input
        {...entrada}
        aria-invalid={erro !== undefined}
        className={`rounded-md border bg-transparent px-3 py-2 text-base outline-none focus:border-blue-600 ${
          erro ? "border-red-600 dark:border-red-400" : "border-black/20 dark:border-white/25"
        }`}
      />
      {erro && (
        <span role="alert" className="text-sm font-normal text-red-600 dark:text-red-400">
          {erro}
        </span>
      )}
    </label>
  );
}
