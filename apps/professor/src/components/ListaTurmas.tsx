"use client";

import Link from "next/link";
import { useRecurso } from "@/lib/recurso";
import type { Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { CartaoTurma } from "./CartaoTurma";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { botaoPrincipal } from "./ui/estilos";

export function ListaTurmas() {
  const turmas = useRecurso<Turma[]>("/api/turmas");

  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-4 p-6">
          <div className="flex items-center justify-between">
            <h1 className="text-xl font-semibold">Minhas turmas</h1>
            <Link href="/turmas/nova" className={`${botaoPrincipal} px-4 py-2`}>
              + Nova turma
            </Link>
          </div>

          {turmas.situacao === "carregando" && <Carregando texto="Carregando turmas..." />}
          {turmas.situacao === "erro" && (
            <ErroDeCarga mensagem={turmas.erro.message} aoTentarNovamente={turmas.recarregar} />
          )}
          {turmas.situacao === "pronto" &&
            (turmas.dados.length === 0 ? (
              <p className="text-sm opacity-70">Você ainda não tem turmas. Crie a primeira em “+ Nova turma”.</p>
            ) : (
              <ul className="grid gap-4 sm:grid-cols-2">
                {turmas.dados.map((turma) => (
                  <CartaoTurma key={turma.id} turma={turma} />
                ))}
              </ul>
            ))}
        </main>
      )}
    </AreaAutenticada>
  );
}
