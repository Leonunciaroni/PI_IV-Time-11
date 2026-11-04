"use client";

import { useEffect, useState } from "react";
import { consultarHistorico, ErroApi } from "@/lib/api";
import { encerrarSessao } from "@/lib/sessao";
import { nomesModos } from "@/lib/chamadas";
import type { HistoricoAluno } from "@/lib/tipos";

export function HistoricoDaTurma({
  token,
  turmaId,
  alunoId,
}: {
  token: string;
  turmaId: string;
  alunoId: string;
}) {
  const [dados, setDados] = useState<HistoricoAluno | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  useEffect(() => {
    const controle = new AbortController();
    consultarHistorico(token, turmaId, alunoId, controle.signal)
      .then((resposta) => {
        if (!controle.signal.aborted) setDados(resposta);
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
            : "Não foi possível consultar o histórico.",
        );
      });
    return () => controle.abort();
  }, [token, turmaId, alunoId]);
  if (erro)
    return (
      <p role="alert" className="mt-4 text-sm text-red-700">
        {erro}
      </p>
    );
  if (!dados)
    return (
      <p role="status" className="mt-4 text-sm text-slate-500">
        Carregando histórico...
      </p>
    );
  if (!dados.aulas.length)
    return (
      <p className="mt-4 text-sm text-slate-500">
        Nenhuma aula encerrada nesta turma.
      </p>
    );
  return (
    <ul
      aria-label="Histórico de presenças"
      className="mt-4 divide-y divide-slate-100 border-t border-slate-100"
    >
      {dados.aulas.map((aula) => (
        <li
          key={aula.sessaoId}
          className="flex justify-between gap-3 py-3 text-xs"
        >
          <span>
            {new Date(aula.data).toLocaleDateString("pt-BR")} ·{" "}
            {nomesModos[aula.modo]}
          </span>
          <span
            className={
              aula.presente
                ? "font-medium text-emerald-700"
                : "font-medium text-red-700"
            }
          >
            {aula.presente ? "Presente" : "Ausente"}
          </span>
        </li>
      ))}
    </ul>
  );
}
