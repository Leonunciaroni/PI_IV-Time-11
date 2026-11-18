"use client";

import { use, useEffect, useSyncExternalStore, type ReactNode } from "react";
import { browser } from "react-dom";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { assinarSessao, encerrarSessao, lerSessao, type Sessao } from "@/lib/sessao";
import { alvoDeToque, botaoSecundario } from "./ui/estilos";
import { Logo } from "./ui/Logo";

/** Renderiza o conteúdo apenas com sessão válida; caso contrário, envia para o login. */
export function AreaAutenticada({ children }: { children: (sessao: Sessao) => ReactNode }) {
  use(browser("A sessão do usuário fica no localStorage."));
  const router = useRouter();
  const sessao = useSyncExternalStore(assinarSessao, lerSessao, () => null);

  useEffect(() => {
    if (!sessao) router.replace("/login");
  }, [sessao, router]);

  if (!sessao) return null;

  // A mudança de sessão faz o efeito acima redirecionar para o login
  function sair() {
    encerrarSessao();
  }

  return (
    <div className="flex flex-1 flex-col">
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-borda-suave bg-superficie px-4 py-3 max-sm:py-2">
        <Link href="/" className={`${alvoDeToque} font-bold tracking-tight whitespace-nowrap`}>
          <Logo />
        </Link>
        <div className="flex items-center gap-3 text-sm whitespace-nowrap">
          <Link href="/perfil" className={`${alvoDeToque} text-base font-medium underline-offset-4 transition hover:text-link-hover hover:underline max-sm:px-1`}>
            Perfil
          </Link>
          <Link href="/salas" className={`${alvoDeToque} text-base font-medium underline-offset-4 transition hover:text-link-hover hover:underline max-sm:px-1`}>
            Salas
          </Link>
          {/* Em telas estreitas o nome sai, para o cabeçalho caber numa linha */}
          <span className="hidden sm:inline">{sessao.usuario.nome}</span>
          <button onClick={sair} className={`${botaoSecundario} px-3 py-1`}>
            Sair
          </button>
        </div>
      </header>
      {children(sessao)}
    </div>
  );
}
