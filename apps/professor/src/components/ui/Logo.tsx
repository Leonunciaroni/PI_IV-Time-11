/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import type { SVGProps } from "react";

const NOME = "Fast Attendance";

/**
 * Símbolo "FA": o F tem um check no lugar do braço do meio, e o traço longo do check sobe e forma
 * a perna esquerda do A. As letras usam a cor do texto (currentColor); o check usa o azul do tema.
 */
export function SimboloLogo({ titulo, ...propriedades }: SVGProps<SVGSVGElement> & { titulo?: string }) {
  return (
    <svg
      viewBox="6 0 60 56"
      fill="none"
      strokeWidth={6}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...(titulo ? { role: "img" } : { "aria-hidden": true })}
      {...propriedades}
    >
      {titulo && <title>{titulo}</title>}
      <path d="M27 14H12V50" stroke="currentColor" />
      <path d="M12 31L24 43L41 7" stroke="currentColor" className="text-primaria-texto" />
      <path d="M41 7L58 43M28.5 34H53.5" stroke="currentColor" />
    </svg>
  );
}

/**
 * Logo do Fast Attendance. O tamanho acompanha o texto em volta (o símbolo mede 1,5 em), então
 * basta mudar o tamanho da fonte do elemento que a contém. `somenteSimbolo` serve para espaços
 * pequenos; nesse caso o símbolo ganha o nome acessível, que no logo completo vem do texto.
 */
export function Logo({ somenteSimbolo = false }: { somenteSimbolo?: boolean }) {
  if (somenteSimbolo) return <SimboloLogo titulo={NOME} className="h-[1.5em] w-auto" />;
  return (
    <span className="inline-flex items-center gap-[0.4em] font-bold tracking-tight whitespace-nowrap">
      <SimboloLogo className="h-[1.5em] w-auto" />
      {NOME}
    </span>
  );
}
