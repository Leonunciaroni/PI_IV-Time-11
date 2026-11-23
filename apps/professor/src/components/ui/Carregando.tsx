/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
/** Indicação de carregamento exibida enquanto uma lista ou página busca dados na API. */
export function Carregando({ texto = "Carregando..." }: { texto?: string }) {
  return (
    <p role="status" className="text-sm text-suave">
      {texto}
    </p>
  );
}
