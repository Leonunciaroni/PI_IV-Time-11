import type { InputHTMLAttributes } from "react";

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  rotulo: string;
}

/** Campo de texto com rótulo, no estilo dos formulários do app. */
export function Campo({ rotulo, ...entrada }: CampoProps) {
  return (
    <label className="flex flex-col gap-1 text-sm font-medium">
      {rotulo}
      <input
        {...entrada}
        className="rounded-md border border-black/20 bg-transparent px-3 py-2 text-base outline-none focus:border-blue-600 dark:border-white/25"
      />
    </label>
  );
}
