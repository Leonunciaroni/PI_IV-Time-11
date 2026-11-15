"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useRecurso } from "@/lib/recurso";
import type { Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { CartaoTurma } from "./CartaoTurma";
import { AvisoDeSucesso } from "./ui/AvisoDeSucesso";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { botaoPrincipal, botaoSecundario, cartao } from "./ui/estilos";

export function ListaTurmas() {
  const turmas = useRecurso<Turma[]>("/api/turmas");
  // A tela de nova turma volta para cá com ?criada=<id> para confirmar o cadastro
  const criadaId = useSearchParams().get("criada");
  const criada = turmas.situacao === "pronto" ? turmas.dados.find((t) => t.id === criadaId) : undefined;

  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-4 p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h1 className="text-xl font-semibold">Minhas turmas</h1>
            <Link href="/turmas/nova" className={`${botaoPrincipal} px-4 py-2`}>
              + Nova turma
            </Link>
          </div>

          {criada && (
            <AvisoDeSucesso>
              Turma “{criada.disciplina} · {criada.nome}” criada. Abra os detalhes para matricular os alunos.
            </AvisoDeSucesso>
          )}

          {turmas.situacao === "carregando" && <Carregando texto="Carregando turmas..." />}
          {turmas.situacao === "erro" && (
            <ErroDeCarga mensagem={turmas.erro.message} aoTentarNovamente={turmas.recarregar} />
          )}
          {turmas.situacao === "pronto" &&
            (turmas.dados.length === 0 ? (
              <div className={`${cartao} flex flex-col items-start gap-3 border-dashed p-6`}>
                <div>
                  <h2 className="font-semibold">Você ainda não tem turmas</h2>
                  <p className="text-sm opacity-70">
                    Crie a primeira turma para matricular os alunos e abrir chamadas.
                  </p>
                </div>
                <Link href="/turmas/nova" className={`${botaoSecundario} px-3 py-2 text-sm`}>
                  Criar a primeira turma
                </Link>
              </div>
            ) : (
              <ul aria-label="Turmas" className="grid gap-4 sm:grid-cols-2">
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
