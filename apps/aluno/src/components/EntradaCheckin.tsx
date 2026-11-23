// Guilherme Preventi Correia
// RA: 25001694

"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { AreaAutenticada } from "./AreaAutenticada";
import { CheckinQr } from "./CheckinQr";
import { CheckinSom } from "./CheckinSom";
import { ErroApi, listarMinhasTurmas, listarSessoesAtivas } from "@/lib/api";
import { encerrarSessao } from "@/lib/sessao";
import type { TurmaDoAluno } from "@/lib/tipos";

export function EntradaCheckin() {
  const parametros = useSearchParams();
  const turmaId = parametros.get("turmaId") ?? "";
  const sessaoId = parametros.get("sessaoId") ?? "";
  const modo = parametros.get("modo") ?? "";
  return (
    <AreaAutenticada>
      {(sessao) => (
        <CanalDaChamada
          key={`${sessao.token}:${turmaId}:${sessaoId}:${modo}`}
          token={sessao.token}
          turmaId={turmaId}
          sessaoId={sessaoId}
          modo={modo}
        />
      )}
    </AreaAutenticada>
  );
}

function CanalDaChamada({
  token,
  turmaId,
  sessaoId,
  modo,
}: {
  token: string;
  turmaId: string;
  sessaoId: string;
  modo: string;
}) {
  const [turma, setTurma] = useState<TurmaDoAluno | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  useEffect(() => {
    const controle = new AbortController();
    Promise.all([
      listarMinhasTurmas(token, controle.signal),
      modo === "SOM" ? listarSessoesAtivas(token, controle.signal) : Promise.resolve(null),
    ]).then(([turmas, ativas]) => {
        if (controle.signal.aborted) return;
        const encontrada = turmas.find(
          (t) =>
            t.id === turmaId &&
            t.chamadaAberta?.id === sessaoId &&
            t.chamadaAberta.modo === modo,
        );
        if (!encontrada || (modo !== "QRCODE" && modo !== "SOM") || (ativas !== null && !ativas.some((s) => s.sessaoId === sessaoId && s.turmaId === turmaId && s.modo === "SOM"))) {
          setErro(
            "Esta chamada não está mais disponível neste canal. Volte ao início e atualize suas turmas.",
          );
        } else setTurma(encontrada);
      })
      .catch((e) => {
        if (controle.signal.aborted) return;
        if (e instanceof ErroApi && e.status === 401) {
          encerrarSessao();
          return;
        }
        setErro(
          e instanceof ErroApi
            ? e.message
            : "Não foi possível verificar a chamada.",
        );
      });
    return () => controle.abort();
  }, [token, turmaId, sessaoId, modo]);
  return (
    <main className="mx-auto w-full max-w-xl px-5 py-10">
      <Link href="/" className="text-sm font-semibold text-[#5040e8]">
        ← Voltar ao início
      </Link>
      {turma?.chamadaAberta?.modo === "QRCODE" ? (
        <CheckinQr turma={turma} />
      ) : turma?.chamadaAberta?.modo === "SOM" ? (
        <CheckinSom turma={turma} token={token} />
      ) : (
        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6">
          <h1 className="text-2xl font-bold">Confirmar presença</h1>
          {erro ? (
            <p role="alert" className="mt-4 text-sm text-red-700">
              {erro}
            </p>
          ) : (
            <p role="status" className="mt-4 text-sm text-slate-500">
              Verificando chamada aberta...
            </p>
          )}
        </section>
      )}
    </main>
  );
}
