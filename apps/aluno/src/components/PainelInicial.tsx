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
  const [versao, setVersao] = useState(0);
  const [atualizando, setAtualizando] = useState(false);
  const [atualizadoEm, setAtualizadoEm] = useState<string | null>(null);

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
        setAtualizadoEm(
          new Date().toLocaleTimeString("pt-BR", {
            hour: "2-digit",
            minute: "2-digit",
          }),
        );
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
        if (!controle.signal.aborted) setAtualizando(false);
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
  }, [sessao.token, versao]);

  const abertas = turmas?.filter((turma) => turma.chamadaAberta) ?? [];
  return (
    <main className="mx-auto w-full max-w-6xl flex-1 px-5 py-8 sm:px-10 sm:py-10">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-marca">
            Seu espaço de aprendizagem
          </p>
          <h1 className="text-3xl font-bold tracking-tight">
            Olá, {sessao.usuario.nome.split(" ")[0]}!
          </h1>
          <p className="mt-2 text-sm text-suave">
            Acompanhe suas turmas e fique por dentro das chamadas.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-suave">
            {atualizadoEm && `Atualizado às ${atualizadoEm}`}
          </span>
          <button
            type="button"
            className="rounded-lg border border-borda bg-superficie px-4 py-2.5 text-sm font-medium hover:bg-superficie-suave disabled:opacity-50"
            disabled={atualizando}
            onClick={() => {
              setAtualizando(true);
              setVersao((valor) => valor + 1);
            }}
          >
            {atualizando ? "Atualizando..." : "Atualizar"}
          </button>
        </div>
      </div>
      {erro && (
        <div
          role="alert"
          className="mb-6 rounded-xl border border-perigo-borda bg-perigo-fundo p-4 text-sm text-perigo-texto"
        >
          {erro}{" "}
          {turmas &&
            "Os dados abaixo são da última atualização. Atualize antes de confirmar presença."}
        </div>
      )}
      {!turmas && !erro && (
        <p
          role="status"
          className="rounded-2xl border border-borda bg-superficie p-8 text-suave"
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
                            ? "Atualize os dados para verificar esta chamada."
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
            <div className="mb-5 flex items-center justify-between gap-3">
              <h2 id="titulo-turmas" className="text-xl font-bold">
                Minhas turmas
              </h2>
              <span className="rounded-full bg-marca/10 px-3 py-1 text-xs font-semibold text-marca">
                {turmas.length} {turmas.length === 1 ? "turma" : "turmas"}
              </span>
            </div>
            {turmas.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-borda-forte bg-superficie p-8 text-center">
                <h3 className="font-semibold">
                  Você ainda não está em nenhuma turma
                </h3>
                <p className="mt-2 text-sm text-suave">
                  Peça ao professor para vincular sua matrícula à turma.
                </p>
              </div>
            ) : (
              <div className="grid gap-5 md:grid-cols-2">
                {turmas.map((turma) => (
                  <article
                    key={turma.id}
                    className="rounded-2xl border border-borda bg-superficie p-6"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <p className="text-xs font-medium text-marca">
                          {turma.nome}
                        </p>
                        <h3 className="mt-1 text-lg font-semibold">
                          {turma.disciplina}
                        </h3>
                        <p className="mt-2 text-sm text-suave">
                          {turma.horarioPadrao || "Horário não informado"}
                        </p>
                      </div>
                      <span
                        aria-hidden="true"
                        className="rounded-xl bg-marca/10 p-3 text-marca"
                      >
                        ▤
                      </span>
                    </div>
                    <div className="mt-6 border-t border-borda pt-5">
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
                          className={`h-full rounded-full ${turma.abaixoDoMinimo ? "bg-amber-500" : "bg-marca"}`}
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
                      className="mt-5 inline-flex text-sm font-semibold text-marca hover:underline"
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
