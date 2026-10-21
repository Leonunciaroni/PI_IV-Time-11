/** Indicação de carregamento exibida enquanto uma lista ou página busca dados na API. */
export function Carregando({ texto = "Carregando..." }: { texto?: string }) {
  return (
    <p role="status" className="text-sm opacity-70">
      {texto}
    </p>
  );
}
