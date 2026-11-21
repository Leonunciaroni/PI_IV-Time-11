"use client";

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useRecurso } from "@/lib/recurso";
import type { Turma } from "@/lib/tipos";
import { filtrarTurmas, ordenarTurmas, type OrdemTurmas } from "@/lib/turmas";
import { AreaAutenticada } from "./AreaAutenticada";
import { CartaoTurma } from "./CartaoTurma";
import { AvisoDeSucesso } from "./ui/AvisoDeSucesso";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { botaoPrincipal, botaoSecundario, cartao } from "./ui/estilos";

// Mesmo destaque de foco dos campos de formulário (Campo.tsx)
const campoFiltro =
  "rounded-controle border border-borda-forte bg-superficie px-3 py-2 text-base font-normal outline-none focus:border-primaria focus:ring-1 focus:ring-primaria";

function contarTurmas(total: number): string {
  return total === 1 ? "1 turma" : `${total} turmas`;
}

export function ListaTurmas() {
  const turmas = useRecurso<Turma[]>("/api/turmas");
  // A tela de nova turma volta para cá com ?criada=<id> para confirmar o cadastro
  const criadaId = useSearchParams().get("criada");
  const criada = turmas.situacao === "pronto" ? turmas.dados.find((t) => t.id === criadaId) : undefined;
  const [busca, setBusca] = useState("");
  const [ordem, setOrdem] = useState<OrdemTurmas>("alfabetica");
  const visiveis = turmas.situacao === "pronto" ? ordenarTurmas(filtrarTurmas(turmas.dados, busca), ordem) : [];

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
                  <p className="text-sm text-suave">
                    Crie a primeira turma para matricular os alunos e abrir chamadas.
                  </p>
                </div>
                <Link href="/turmas/nova" className={`${botaoSecundario} px-3 py-2 text-sm`}>
                  Criar a primeira turma
                </Link>
              </div>
            ) : (
              <>
                <div className="flex flex-wrap items-end gap-3">
                  <label className="flex min-w-0 flex-1 basis-60 flex-col gap-1 text-sm font-medium">
                    Buscar turma
                    <input
                      type="search"
                      value={busca}
                      onChange={(e) => setBusca(e.target.value)}
                      placeholder="Disciplina ou nome da turma"
                      className={campoFiltro}
                    />
                  </label>
                  <label className="flex flex-col gap-1 text-sm font-medium">
                    Ordenar por
                    <select
                      value={ordem}
                      onChange={(e) => setOrdem(e.target.value as OrdemTurmas)}
                      className={campoFiltro}
                    >
                      <option value="alfabetica">Disciplina (A–Z)</option>
                      <option value="recentes">Criadas mais recentemente</option>
                    </select>
                  </label>
                </div>

                {/* Anuncia ao leitor de tela quantas turmas sobraram enquanto o professor digita */}
                <p role="status" className="text-sm text-suave">
                  {visiveis.length === turmas.dados.length
                    ? contarTurmas(turmas.dados.length)
                    : `${visiveis.length} de ${contarTurmas(turmas.dados.length)}`}
                </p>

                {visiveis.length === 0 ? (
                  <div className={`${cartao} flex flex-col items-start gap-3 border-dashed p-6`}>
                    <p className="text-sm">Nenhuma turma encontrada para “{busca.trim()}”.</p>
                    <button type="button" onClick={() => setBusca("")} className={`${botaoSecundario} px-3 py-2 text-sm`}>
                      Limpar busca
                    </button>
                  </div>
                ) : (
                  <ul aria-label="Turmas" className="grid gap-4 sm:grid-cols-2">
                    {visiveis.map((turma) => (
                      <CartaoTurma key={turma.id} turma={turma} />
                    ))}
                  </ul>
                )}
              </>
            ))}
        </main>
      )}
    </AreaAutenticada>
  );
}
