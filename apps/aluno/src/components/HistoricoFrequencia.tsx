"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { AreaAutenticada } from "./AreaAutenticada";
import { ErroApi, consultarHistorico, listarMinhasTurmas } from "@/lib/api";
import { nomesModos } from "@/lib/chamadas";
import { encerrarSessao, type Sessao } from "@/lib/sessao";
import type { HistoricoAluno, TurmaDoAluno } from "@/lib/tipos";

const botao =
  "rounded-lg bg-[#5040e8] px-4 py-2.5 text-sm font-semibold text-white hover:bg-[#4332cd] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5040e8]";

function mensagemDeErro(erro: unknown): string {
  if (erro instanceof ErroApi && erro.status === 401) {
    encerrarSessao();
    return "Sua sessão expirou. Entre novamente.";
  }
  return erro instanceof ErroApi
    ? erro.message
    : "Não foi possível carregar o histórico. Tente novamente.";
}

export function HistoricoFrequencia() {
  const parametros = useSearchParams();
  const turmaId = parametros.get("turmaId");
  return (
    <AreaAutenticada>
      {(sessao) => (
        <HistoricoDoAluno
          key={sessao.token}
          sessao={sessao}
          turmaId={turmaId}
        />
      )}
    </AreaAutenticada>
  );
}

function HistoricoDoAluno({
  sessao,
  turmaId,
}: {
  sessao: Sessao;
  turmaId: string | null;
}) {
  const router = useRouter();
  const [turmas, setTurmas] = useState<TurmaDoAluno[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    const controle = new AbortController();
    listarMinhasTurmas(sessao.token, controle.signal)
      .then((dados) => {
        if (!controle.signal.aborted) setTurmas(dados);
      })
      .catch((e) => {
        if (!controle.signal.aborted) setErro(mensagemDeErro(e));
      });
    return () => controle.abort();
  }, [sessao.token, tentativa]);
  const selecionada = turmaId
    ? turmas?.find((turma) => turma.id === turmaId)
    : turmas?.[0];

  return (
    <main className="mx-auto w-full max-w-4xl flex-1 px-5 py-8 sm:px-10 sm:py-10">
      <Link
        href="/"
        className="text-sm font-semibold text-[#5040e8] hover:underline"
      >
        ← Voltar ao início
      </Link>
      <header className="mb-8 mt-6">
        <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[#5040e8]">
          Sua jornada em sala
        </p>
        <h1 className="text-3xl font-bold tracking-tight">
          Histórico de frequência
        </h1>
        <p className="mt-2 text-sm text-slate-500">
          Consulte suas presenças e o modo de chamada de cada aula.
        </p>
      </header>
      {erro ? (
        <div
          role="alert"
          className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800"
        >
          <p>{erro}</p>
          <button
            type="button"
            className={`${botao} mt-4`}
            onClick={() => {
              setErro(null);
              setTurmas(null);
              setTentativa((valor) => valor + 1);
            }}
          >
            Tentar novamente
          </button>
        </div>
      ) : !turmas ? (
        <p
          role="status"
          className="rounded-xl bg-white p-6 text-sm text-slate-500"
        >
          Carregando suas turmas...
        </p>
      ) : turmas.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
          <h2 className="font-semibold">
            Você ainda não está em nenhuma turma
          </h2>
          <p className="mt-2 text-sm text-slate-500">
            O histórico ficará disponível quando seu professor vincular sua
            matrícula a uma turma.
          </p>
        </div>
      ) : (
        <>
          <label
            htmlFor="turma-historico"
            className="mb-2 block text-sm font-medium"
          >
            Turma
          </label>
          <select
            id="turma-historico"
            value={selecionada?.id ?? ""}
            onChange={(evento) =>
              router.replace(
                `/historico?turmaId=${encodeURIComponent(evento.target.value)}`,
              )
            }
            className="mb-6 w-full rounded-lg border border-slate-200 bg-white px-3 py-3 text-sm focus:outline-2 focus:outline-[#5040e8]"
          >
            {!selecionada && (
              <option value="" disabled>
                Selecione uma das suas turmas
              </option>
            )}
            {turmas.map((turma) => (
              <option key={turma.id} value={turma.id}>
                {turma.disciplina} · {turma.nome}
              </option>
            ))}
          </select>
          {selecionada ? (
            <DetalhesHistorico
              key={`${sessao.token}:${selecionada.id}`}
              token={sessao.token}
              alunoId={sessao.usuario.id}
              turma={selecionada}
            />
          ) : (
            <p
              role="alert"
              className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900"
            >
              Esta turma não está disponível para sua conta. Escolha uma das
              suas turmas acima.
            </p>
          )}
        </>
      )}
    </main>
  );
}

function DetalhesHistorico({
  token,
  alunoId,
  turma,
}: {
  token: string;
  alunoId: string;
  turma: TurmaDoAluno;
}) {
  const [dados, setDados] = useState<HistoricoAluno | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    const controle = new AbortController();
    consultarHistorico(token, turma.id, alunoId, controle.signal)
      .then((resposta) => {
        if (!controle.signal.aborted) setDados(resposta);
      })
      .catch((e) => {
        if (!controle.signal.aborted) setErro(mensagemDeErro(e));
      });
    return () => controle.abort();
  }, [token, turma.id, alunoId, tentativa]);
  if (erro)
    return (
      <div
        role="alert"
        className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800"
      >
        <p>{erro}</p>
        <button
          type="button"
          className={`${botao} mt-4`}
          onClick={() => {
            setErro(null);
            setDados(null);
            setTentativa((valor) => valor + 1);
          }}
        >
          Tentar novamente
        </button>
      </div>
    );
  if (!dados)
    return (
      <p
        role="status"
        className="rounded-xl bg-white p-6 text-sm text-slate-500"
      >
        Carregando as aulas da turma...
      </p>
    );
  const aulas = [...dados.aulas].sort(
    (a, b) => Date.parse(b.data) - Date.parse(a.data),
  );
  return (
    <>
      <section
        aria-label="Resumo de frequência"
        className="mb-6 rounded-2xl bg-[#172033] p-6 text-white sm:p-8"
      >
        <p className="text-sm text-slate-400">{turma.nome}</p>
        <h2 className="mt-1 text-xl font-semibold sm:text-2xl">
          {turma.disciplina}
        </h2>
        <div className="mt-6 flex flex-wrap items-end justify-between gap-6">
          <div>
            <p className="text-sm text-slate-300">Frequência acumulada</p>
            <p className="mt-1 text-4xl font-bold">
              {dados.aulasDadas
                ? `${dados.percentual.toLocaleString("pt-BR")}%`
                : "—"}
            </p>
          </div>
          <dl className="flex gap-8 text-sm">
            <div>
              <dt className="text-slate-400">Presenças</dt>
              <dd className="mt-1 text-xl font-semibold">{dados.presencas}</dd>
            </div>
            <div>
              <dt className="text-slate-400">Aulas encerradas</dt>
              <dd className="mt-1 text-xl font-semibold">{dados.aulasDadas}</dd>
            </div>
          </dl>
        </div>
        {dados.abaixoDoMinimo && (
          <p className="mt-5 text-sm text-amber-300">
            Sua frequência está abaixo do mínimo de {dados.minimo}%.
          </p>
        )}
      </section>
      {!aulas.length ? (
        <section className="rounded-2xl border border-slate-200 bg-white p-8 text-center">
          <h2 className="font-semibold">Nenhuma aula encerrada nesta turma</h2>
          <p className="mt-2 text-sm text-slate-500">
            As presenças aparecerão após o encerramento da primeira chamada.
          </p>
        </section>
      ) : (
        <section
          aria-labelledby="titulo-aulas"
          className="overflow-hidden rounded-2xl border border-slate-200 bg-white"
        >
          <h2
            id="titulo-aulas"
            className="border-b border-slate-200 px-5 py-4 text-lg font-semibold"
          >
            Aulas da turma
          </h2>
          <table className="w-full table-fixed text-left text-sm">
            <caption className="sr-only">
              Todas as aulas encerradas, da mais recente para a mais antiga, com
              modo e situação da presença
            </caption>
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                <th scope="col" className="px-3 py-3 sm:px-5">
                  Data
                </th>
                <th scope="col" className="px-3 py-3 sm:px-5">
                  Modo
                </th>
                <th scope="col" className="px-3 py-3 sm:px-5">
                  Situação
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {aulas.map((aula) => (
                <tr key={aula.sessaoId}>
                  <td className="px-3 py-4 sm:px-5">
                    <time dateTime={aula.data}>
                      {new Date(aula.data).toLocaleDateString("pt-BR")}
                      <span className="mt-1 block text-xs text-slate-500">
                        {new Date(aula.data).toLocaleTimeString("pt-BR", {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </span>
                    </time>
                  </td>
                  <td className="px-3 py-4 text-slate-600 sm:px-5">
                    {nomesModos[aula.modo]}
                  </td>
                  <td className="px-3 py-4 sm:px-5">
                    <span
                      className={`inline-block rounded-full px-2 py-1 text-xs font-semibold ${aula.presente ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-800"}`}
                    >
                      {aula.presente ? "Presente" : "Ausente"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
      <p className="mt-6 text-center text-xs leading-relaxed text-slate-500">
        A frequência considera somente aulas encerradas. Sem registro de
        presença, a aula conta como ausência.
      </p>
    </>
  );
}
