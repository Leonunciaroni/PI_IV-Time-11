import type { ButtonHTMLAttributes } from "react";
import { botaoPerigo, botaoPerigoContorno, botaoPrincipal, botaoSecundario } from "./estilos";

type Variante = "principal" | "secundario" | "perigo" | "perigo-contorno";

const ESTILOS: Record<Variante, string> = {
  principal: `${botaoPrincipal} px-4 py-2`,
  secundario: `${botaoSecundario} px-3 py-2`,
  perigo: `${botaoPerigo} px-4 py-2`,
  "perigo-contorno": `${botaoPerigoContorno} px-4 py-2`,
};

interface BotaoProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Principal (padrão), secundário com contorno, ou de perigo para ações sem volta. */
  variante?: Variante;
}

/** Botão do app; o padrão é a ação principal. */
export function Botao({ type = "button", variante = "principal", ...botao }: BotaoProps) {
  return <button type={type} {...botao} className={ESTILOS[variante]} />;
}
