/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Guilherme Preventi Correia
// RA: 25001694

"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AreaAutenticada } from "./AreaAutenticada";
import { ErroApi, listarMinhasTurmas } from "@/lib/api";
import { caminhoCheckin, nomesModos } from "@/lib/chamadas";
import { encerrarSessao, type Sessao } from "@/lib/sessao";
import type { TurmaDoAluno } from "@/lib/tipos";

// Fica dentro do bloco roxo das chamadas: branco com texto roxo, para não sumir no fundo
const botao =
  "rounded-lg bg-white px-4 py-2.5 text-sm font-semibold text-[#3b2db5] hover:bg-white/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white disabled:opacity-50";

export function PainelInicial() {
  return (
    <AreaAutenticada>
      {(sessao) => <InicioDoAluno key={sessao.token} sessao={sessao} />}
    </AreaAutenticada>
  );
}

function InicioDoAluno({ sessao }: { sessao: Sessao }) {
  const [turmas, setTurmas] = useState<TurmaDoAluno[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    const controle = new AbortController();
    let buscando = false;
    async function carregar() {
      if (buscando || document.visibilityState === "hidden") return;
      buscando = true;
      try {
        const resposta = await listarMinhasTurmas(
          sessao.token,
          controle.signal,
        );
        if (controle.signal.aborted) return;
        setTurmas(resposta);
        setErro(null);
      } catch (e) {
        if (controle.signal.aborted) return;
        if (e instanceof ErroApi && e.status === 401) {
          encerrarSessao();
          return;
        }
        setErro(
          e instanceof ErroApi
            ? e.message
            : "Não foi possível carregar suas turmas.",
        );
      } finally {
        buscando = false;
      }
    }
    void carregar();
    const intervalo = window.setInterval(carregar, 20000);
    document.addEventListener("visibilitychange", carregar);
    return () => {
      controle.abort();
      window.clearInterval(intervalo);
      document.removeEventListener("visibilitychange", carregar);
    };
  }, [sessao.token]);

  const abertas = turmas?.filter((turma) => turma.chamadaAberta) ?? [];
  return (
    <main className="mx-auto w-full max-w-6xl flex-1 px-5 py-8 sm:px-10 sm:py-10">
      <h1 className="mb-8 text-3xl font-bold tracking-tight">
        Chamadas e frequência
      </h1>
      {erro && (
        <div
          role="alert"
          className="mb-6 rounded-lg border border-perigo-borda bg-perigo-fundo p-4 text-sm text-perigo-texto"
        >
          {erro}{" "}
          {turmas &&
            "Os dados abaixo são da última atualização. A página tenta de novo a cada 20 segundos."}
        </div>
      )}
      {!turmas && !erro && (
        <p
          role="status"
          className="rounded-xl border border-borda bg-superficie p-8 text-suave"
        >
          Carregando suas turmas e chamadas...
        </p>
      )}
      {turmas && (
        <>
          <section
            aria-labelledby="titulo-chamadas"
            className="mb-9 overflow-hidden rounded-2xl bg-[#3b2db5] p-6 text-white sm:p-8"
          >
            <div className="mb-5 flex items-center gap-2">
              <span
                aria-hidden="true"
                className={`size-2 rounded-full ${abertas.length ? "bg-emerald-400" : "bg-slate-400"}`}
              />
              <h2
                id="titulo-chamadas"
                className="text-xs font-semibold uppercase tracking-widest"
              >
                {abertas.length ? "Chamada aberta" : "Suas chamadas"}
              </h2>
            </div>
            {abertas.length === 0 ? (
              <div>
                <h3 className="text-2xl font-semibold">
                  Nenhuma chamada aberta no momento
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-violet-200">
                  Quando um professor abrir a chamada de uma das suas turmas,
                  ela aparecerá aqui.
                </p>
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2">
                {abertas.map((turma) => {
                  const caminho = caminhoCheckin(turma);
                  return (
                    <article
                      key={turma.id}
                      className="rounded-xl border border-white/10 bg-white/5 p-5"
                    >
                      <p className="text-xs text-violet-200">{turma.nome}</p>
                      <h3 className="mt-1 text-xl font-semibold">
                        {turma.disciplina}
                      </h3>
                      <p className="mt-3 text-sm text-violet-200">
                        Modo: {nomesModos[turma.chamadaAberta!.modo]}
                      </p>
                      {caminho && !erro ? (
                        <Link
                          href={caminho}
                          className={`${botao} mt-5 inline-flex`}
                        >
                          Confirmar presença
                        </Link>
                      ) : (
                        <p className="mt-4 text-sm leading-relaxed text-violet-200">
                          {erro
                            ? "Aguarde a próxima atualização para verificar esta chamada."
                            : "A presença será registrada pelo professor. Aguarde a chamada do seu nome."}
                        </p>
                      )}
                    </article>
                  );
                })}
              </div>
            )}
          </section>
          <section aria-labelledby="titulo-turmas">
            <div className="mb-5 flex items-baseline justify-between gap-3">
              <h2 id="titulo-turmas" className="text-xl font-bold">
                Minhas turmas
              </h2>
              <span className="text-sm text-suave">
                {turmas.length} {turmas.length === 1 ? "turma" : "turmas"}
              </span>
            </div>
            {turmas.length === 0 ? (
              <div className="rounded-xl border border-dashed border-borda-forte bg-superficie p-8 text-center">
                <h3 className="font-semibold">
                  Você ainda não está em nenhuma turma
                </h3>
                <p className="mt-2 text-sm text-suave">
                  Peça ao professor para vincular sua matrícula à turma.
                </p>
              </div>
            ) : (
              // Uma turma por linha, com espaço entre elas: no desktop, a frequência ocupa a coluna da
              // direita e o link fica embaixo dos dados da turma, sem mudar a ordem de leitura.
              <div className="flex flex-col gap-3">
                {turmas.map((turma) => (
                  <article
                    key={turma.id}
                    className="grid gap-x-10 rounded-xl border border-borda bg-superficie p-5 sm:grid-cols-[minmax(0,1fr)_minmax(0,18rem)] sm:px-6"
                  >
                    <div>
                      <p className="text-sm font-semibold text-marca">
                        {turma.nome}
                      </p>
                      <h3 className="mt-1 text-xl font-semibold">
                        {turma.disciplina}
                      </h3>
                      <p className="mt-1 text-sm text-suave">
                        {turma.horarioPadrao || "Horário não informado"}
                      </p>
                    </div>
                    <div className="mt-4 sm:row-span-2 sm:mt-0 sm:self-center">
                      <div className="flex items-baseline justify-between gap-3">
                        <span className="text-sm text-suave">
                          Sua frequência
                        </span>
                        <strong className="text-2xl">
                          {turma.aulasDadas
                            ? `${turma.percentual.toLocaleString("pt-BR")}%`
                            : "—"}
                        </strong>
                      </div>
                      <div
                        role="progressbar"
                        aria-label={`Frequência em ${turma.disciplina}`}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={turma.percentual}
                        aria-valuetext={
                          turma.aulasDadas
                            ? `${turma.percentual}%`
                            : "Sem aulas encerradas"
                        }
                        className="mt-3 h-2 overflow-hidden rounded-full bg-superficie-suave"
                      >
                        <div
                          style={{ width: `${turma.percentual}%` }}
                          // Laranja abaixo do mínimo e verde em dia; sem aulas encerradas a barra fica vazia
                          className={`h-full rounded-full ${turma.abaixoDoMinimo ? "bg-amber-500" : "bg-emerald-600"}`}
                        />
                      </div>
                      <p
                        className={`mt-3 text-xs ${turma.abaixoDoMinimo ? "text-atencao-texto" : "text-suave"}`}
                      >
                        {turma.aulasDadas
                          ? `${turma.presencas} presença(s) em ${turma.aulasDadas} aula(s) encerrada(s).${turma.abaixoDoMinimo ? ` Abaixo do mínimo de ${turma.minimo}%.` : ""}`
                          : "Sua frequência aparecerá após a primeira aula encerrada."}
                      </p>
                    </div>
                    <Link
                      href={`/historico?turmaId=${encodeURIComponent(turma.id)}`}
                      className="mt-4 justify-self-start text-sm font-semibold text-marca hover:underline sm:mt-3"
                    >
                      Ver histórico
                    </Link>
                  </article>
                ))}
              </div>
            )}
          </section>
          <p className="mt-8 text-center text-xs leading-relaxed text-suave">
            As chamadas são atualizadas a cada 20 segundos enquanto esta página
            está visível.
            <br />A frequência considera somente as aulas encerradas.
          </p>
        </>
      )}
    </main>
  );
}
