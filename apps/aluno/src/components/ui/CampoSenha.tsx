import { useId, useState, type InputHTMLAttributes } from "react";

interface CampoSenhaProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  rotulo: string;
}

/** Olho aberto (a senha está escondida) ou riscado (a senha está visível). */
function IconeOlho({ riscado }: { riscado: boolean }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden className="size-5">
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {riscado && <path d="M4 4l16 16" />}
    </svg>
  );
}

/**
 * Campo de senha no estilo do Campo, com um botão de olho dentro dele para mostrar ou esconder a
 * senha, como no app do professor. O botão diz ao leitor de tela qual campo controla e se a senha
 * está visível.
 */
export function CampoSenha({ rotulo, ...entrada }: CampoSenhaProps) {
  const id = useId();
  const [visivel, setVisivel] = useState(false);

  return (
    <div className="flex flex-col gap-1 text-sm font-medium">
      <label htmlFor={id}>{rotulo}</label>
      <div className="relative">
        <input
          {...entrada}
          id={id}
          type={visivel ? "text" : "password"}
          className="w-full rounded-lg border border-borda-forte bg-superficie py-2 pr-12 pl-3 text-base outline-none focus:border-marca focus:ring-1 focus:ring-marca"
        />
        <button
          type="button"
          onClick={() => setVisivel(!visivel)}
          aria-controls={id}
          aria-pressed={visivel}
          aria-label="Mostrar senha"
          title={visivel ? "Ocultar senha" : "Mostrar senha"}
          className="absolute inset-y-1 right-1 flex aspect-square items-center justify-center rounded-lg text-suave transition hover:bg-superficie-suave hover:text-marca focus-visible:outline-2 focus-visible:outline-marca"
        >
          <IconeOlho riscado={visivel} />
        </button>
      </div>
    </div>
  );
}
