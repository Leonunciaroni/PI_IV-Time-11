import type { ButtonHTMLAttributes } from "react";

/** Botão de ação principal. */
export function Botao({ type = "button", ...botao }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type={type}
      {...botao}
      className="rounded-md bg-blue-600 px-4 py-2 font-semibold text-white transition hover:bg-blue-700 disabled:cursor-wait disabled:opacity-60"
    />
  );
}
