import type { ButtonHTMLAttributes } from "react";
import { botaoPrincipal, botaoSecundario } from "./estilos";

interface BotaoProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Principal (padrão) ou secundário, com contorno. */
  variante?: "principal" | "secundario";
}

/** Botão do app; o padrão é a ação principal. */
export function Botao({ type = "button", variante = "principal", ...botao }: BotaoProps) {
  const estilo = variante === "principal" ? `${botaoPrincipal} px-4 py-2` : `${botaoSecundario} px-3 py-2`;
  return <button type={type} {...botao} className={estilo} />;
}
