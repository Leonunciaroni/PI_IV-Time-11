// Guilherme Preventi Correia
// RA: 25001694

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
  "rounded-lg bg-[#3b2db5] px-4 py-2.5 text-sm font-semibold text-white hover:bg-[#2f2491] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca";

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
        className="text-sm font-semibold text-marca hover:underline"
      >
        ← Voltar ao início
      </Link>
      <header className="mb-8 mt-6">
        <h1 className="text-3xl font-bold tracking-tight">
          Histórico de frequência
        </h1>
      </header>
      {erro ? (
        <div
          role="alert"
          className="rounded-lg border border-perigo-borda bg-perigo-fundo p-5 text-sm text-perigo-texto"
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
          className="rounded-xl bg-superficie p-6 text-sm text-suave"
        >
          Carregando suas turmas...
        </p>
      ) : turmas.length === 0 ? (
        <div className="rounded-xl border border-dashed border-borda-forte bg-superficie p-8 text-center">
          <h2 className="font-semibold">
            Você ainda não está em nenhuma turma
          </h2>
          <p className="mt-2 text-sm text-suave">
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
            className="mb-6 w-full rounded-lg border border-borda bg-superficie px-3 py-3 text-sm focus:outline-2 focus:outline-marca"
          >
            {!selecionada && (
              <option value="" disabled>
                Selecione uma das suas turmas
              </option>
            )}
            {turmas.map((turma) => (
              <option key={turma.id} value={turma.id}>
                {turma.disciplina} ({turma.nome})
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
              className="rounded-lg border border-atencao-borda bg-atencao-fundo p-5 text-sm text-atencao-texto"
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
        className="rounded-lg border border-perigo-borda bg-perigo-fundo p-5 text-sm text-perigo-texto"
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
        className="rounded-xl bg-superficie p-6 text-sm text-suave"
      >
        Carregando as aulas da turma...
      </p>
    );
  const aulas = [...dados.aulas].sort(
    (a, b) => Date.parse(b.data) - Date.parse(a.data),
  );
  // Abaixo do mínimo, o bloco fica no mesmo laranja da barra de frequência do início; sobre o
  // laranja, o texto é marrom escuro para ter contraste. O aviso em texto diz o mesmo que a cor.
  const textoSuave = dados.abaixoDoMinimo ? "text-amber-950/80" : "text-violet-200";
  return (
    <>
      <section
        aria-label="Resumo de frequência"
        className={`mb-6 rounded-2xl p-6 sm:p-8 ${dados.abaixoDoMinimo ? "bg-amber-500 text-amber-950" : "bg-[#3b2db5] text-white"}`}
      >
        <p className={`text-sm ${textoSuave}`}>{turma.nome}</p>
        <h2 className="mt-1 text-xl font-semibold sm:text-2xl">
          {turma.disciplina}
        </h2>
        <div className="mt-6 flex flex-wrap items-end justify-between gap-6">
          <div>
            <p className={`text-sm ${textoSuave}`}>Frequência acumulada</p>
            <p className="mt-1 text-4xl font-bold">
              {dados.aulasDadas
                ? `${dados.percentual.toLocaleString("pt-BR")}%`
                : "—"}
            </p>
          </div>
          <dl className="flex gap-8 text-sm">
            <div>
              <dt className={textoSuave}>Presenças</dt>
              <dd className="mt-1 text-xl font-semibold">{dados.presencas}</dd>
            </div>
            <div>
              <dt className={textoSuave}>Aulas encerradas</dt>
              <dd className="mt-1 text-xl font-semibold">{dados.aulasDadas}</dd>
            </div>
          </dl>
        </div>
        {dados.abaixoDoMinimo && (
          // Selo claro com texto vermelho: vermelho direto sobre o laranja não teria contraste
          <p className="mt-5 inline-block rounded-lg bg-white px-3 py-2 text-sm text-red-700 shadow-sm">
            <strong className="font-bold">Cuidado:</strong> sua frequência está abaixo do mínimo de{" "}
            {dados.minimo}%.
          </p>
        )}
        {!dados.abaixoDoMinimo && dados.aulasDadas > 0 && (
          // Mesmo formato do aviso de cuidado, em verde; sem aulas encerradas não há o que dizer
          <p className="mt-5 inline-block rounded-lg bg-white px-3 py-2 text-sm text-emerald-700 shadow-sm">
            <strong className="font-bold">Frequência em dia</strong> (mínimo de {dados.minimo}%).
          </p>
        )}
      </section>
      {!aulas.length ? (
        <section className="rounded-xl border border-borda bg-superficie p-8 text-center">
          <h2 className="font-semibold">Nenhuma aula encerrada nesta turma</h2>
          <p className="mt-2 text-sm text-suave">
            As presenças aparecerão após o encerramento da primeira chamada.
          </p>
        </section>
      ) : (
        <section
          aria-labelledby="titulo-aulas"
          className="overflow-hidden rounded-xl border border-borda bg-superficie"
        >
          <div className="border-b border-borda px-5 py-4">
            <h2 id="titulo-aulas" className="text-lg font-semibold">
              Aulas da turma
            </h2>
            <p className="mt-1 text-sm text-suave">
              Sem registro de presença, a aula conta como ausência.
            </p>
          </div>
          <table className="w-full table-fixed text-left text-sm">
            <caption className="sr-only">
              Todas as aulas encerradas, da mais recente para a mais antiga, com
              modo e situação da presença
            </caption>
            <thead className="bg-superficie-suave text-xs text-suave">
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
            <tbody className="divide-y divide-borda">
              {aulas.map((aula) => (
                <tr key={aula.sessaoId}>
                  <td className="px-3 py-4 sm:px-5">
                    <time dateTime={aula.data}>
                      {new Date(aula.data).toLocaleDateString("pt-BR")}
                      <span className="mt-1 block text-xs text-suave">
                        {new Date(aula.data).toLocaleTimeString("pt-BR", {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </span>
                    </time>
                  </td>
                  <td className="px-3 py-4 text-suave sm:px-5">
                    {nomesModos[aula.modo]}
                  </td>
                  <td className="px-3 py-4 sm:px-5">
                    <span
                      className={`inline-block rounded-full px-2 py-1 text-xs font-semibold ${aula.presente ? "bg-sucesso-fundo text-sucesso-texto" : "bg-perigo-fundo text-perigo-texto"}`}
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
    </>
  );
}
