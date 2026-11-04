"use client";

import { use, useEffect, useSyncExternalStore, type ReactNode } from "react";
import { browser } from "react-dom";
import { useRouter } from "next/navigation";
import { assinarSessao, encerrarSessao, lerSessao, type Sessao } from "@/lib/sessao";
import { botaoSecundario } from "./ui/estilos";

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
      <header className="flex items-center justify-between border-b border-borda-suave px-4 py-3">
        <span className="font-bold tracking-tight">FAST ATTENDANCE</span>
        <div className="flex items-center gap-3 text-sm">
          <span>{sessao.usuario.nome}</span>
          <button onClick={sair} className={`${botaoSecundario} px-3 py-1`}>
            Sair
          </button>
        </div>
      </header>
      {children(sessao)}
    </div>
  );
}
