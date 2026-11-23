/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Guilherme Preventi Correia
// RA: 25001694

"use client";

import { use, useEffect, useSyncExternalStore, type ReactNode } from "react";
import { browser } from "react-dom";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { assinarSessao, lerSessao, type Sessao } from "@/lib/sessao";
import { SimboloLogo } from "./ui/Logo";

/** Renderiza o conteúdo apenas com sessão válida; caso contrário, envia para o login. */
export function AreaAutenticada({
  children,
}: {
  children: (sessao: Sessao) => ReactNode;
}) {
  use(browser("A sessão do usuário fica no localStorage."));
  const router = useRouter();
  const sessao = useSyncExternalStore(assinarSessao, lerSessao, () => null);

  useEffect(() => {
    if (!sessao) router.replace("/login");
  }, [sessao, router]);

  // Sair fica no perfil (Perfil.tsx); a mudança de sessão faz o efeito acima redirecionar
  if (!sessao) return null;

  return (
    <div className="flex flex-1 flex-col bg-background text-foreground">
      <header className="border-b border-borda bg-superficie px-5 py-4 sm:px-10">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4">
          <Link href="/" aria-label="Fast Attendance — início do aluno" className="flex items-center gap-3 rounded-lg font-bold tracking-tight focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-marca">
            <SimboloLogo className="h-9 w-auto" />
            <span>
              Fast Attendance
              <span className="block text-xs font-normal tracking-normal text-suave">
                Área do aluno
              </span>
            </span>
          </Link>
          {/* O nome leva ao perfil, onde fica o botão de sair; no celular ele também aparece,
              porque é o único caminho até lá */}
          <Link
            href="/perfil"
            aria-label={`${sessao.usuario.nome}, meu perfil`}
            className="max-w-40 truncate rounded text-sm font-medium transition-colors hover:text-marca focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca sm:max-w-none"
          >
            {sessao.usuario.nome}
          </Link>
        </div>
      </header>
      {children(sessao)}
    </div>
  );
}
