/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Guilherme Preventi Correia
// RA: 25001694

"use client";

import { use, useEffect, useSyncExternalStore, type ReactNode } from "react";
import { browser } from "react-dom";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { assinarSessao, lerSessao, type Sessao } from "@/lib/sessao";
import { alvoDeToque } from "./ui/estilos";
import { Logo } from "./ui/Logo";

/** Renderiza o conteúdo apenas com sessão válida; caso contrário, envia para o login. */
export function AreaAutenticada({ children }: { children: (sessao: Sessao) => ReactNode }) {
  use(browser("A sessão do usuário fica no localStorage."));
  const router = useRouter();
  const sessao = useSyncExternalStore(assinarSessao, lerSessao, () => null);

  useEffect(() => {
    if (!sessao) router.replace("/login");
  }, [sessao, router]);

  // Sair fica no perfil (Perfil.tsx); a mudança de sessão faz o efeito acima redirecionar
  if (!sessao) return null;

  return (
    <div className="flex flex-1 flex-col">
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-borda-suave bg-superficie px-4 py-3 max-sm:py-2">
        <Link href="/" className={`${alvoDeToque} font-bold tracking-tight whitespace-nowrap`}>
          <Logo />
        </Link>
        <div className="flex items-center gap-3 text-sm whitespace-nowrap">
          <Link href="/salas" className={`${alvoDeToque} text-base font-medium underline-offset-4 transition hover:text-link-hover hover:underline max-sm:px-1`}>
            Salas
          </Link>
          {/* O nome leva ao perfil, onde fica o botão de sair; no celular ele também aparece,
              porque é o único caminho até lá */}
          <Link
            href="/perfil"
            aria-label={`${sessao.usuario.nome}, meu perfil`}
            className={`${alvoDeToque} max-w-40 truncate rounded text-base font-medium transition-colors hover:text-primaria-texto max-sm:px-1 sm:max-w-none`}
          >
            {sessao.usuario.nome}
          </Link>
        </div>
      </header>
      {children(sessao)}
    </div>
  );
}
