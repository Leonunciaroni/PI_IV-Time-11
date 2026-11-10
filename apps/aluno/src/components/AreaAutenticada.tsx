"use client";

import { use, useEffect, useSyncExternalStore, type ReactNode } from "react";
import { browser } from "react-dom";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  assinarSessao,
  encerrarSessao,
  lerSessao,
  type Sessao,
} from "@/lib/sessao";

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

  if (!sessao) return null;

  // A mudança de sessão faz o efeito acima redirecionar para o login
  function sair() {
    encerrarSessao();
  }

  return (
    <div className="flex flex-1 flex-col bg-[#f8fafc] text-[#172033]">
      <header className="border-b border-[#e2e8f0] bg-white px-5 py-4 sm:px-10">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4">
          <Link href="/" aria-label="Fast Attendance — início do aluno" className="flex items-center gap-3 rounded-lg font-bold tracking-tight focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#5040e8]">
            <span
              aria-hidden="true"
              className="flex size-10 items-center justify-center rounded-xl bg-[#5040e8] text-xl text-white"
            >
              ✓
            </span>
            <span>
              Fast Attendance
              <span className="block text-xs font-normal tracking-normal text-slate-500">
                Área do aluno
              </span>
            </span>
          </Link>
          <div className="flex items-center gap-3 text-sm">
            <span className="hidden max-w-64 truncate sm:inline" title={sessao.usuario.nome}>{sessao.usuario.nome}</span>
            <button
              onClick={sair}
              className="rounded-lg border border-[#e2e8f0] px-4 py-2 font-medium hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-[#5040e8]"
            >
              Sair
            </button>
          </div>
        </div>
      </header>
      {children(sessao)}
    </div>
  );
}
