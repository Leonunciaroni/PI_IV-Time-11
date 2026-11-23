// Guilherme Preventi Correia
// RA: 25001694

import type { Metadata } from "next";
import Link from "next/link";
import { FormularioCadastro } from "@/components/FormularioCadastro";

export const metadata: Metadata = {
  title: "Cadastro do professor — Fast Attendance",
};

const beneficios = [
  [
    "Lançamento instantâneo",
    "Lance presença para turmas inteiras com apenas um clique."
  ],
  [
    "Chamada com proximidade",
    "Confirme presenças por QR Code ou onda sonora."
  ],
  [
    "Fácil organização",
    "Organize turmas e acompanhe os alunos em cada aula."
  ]
];

export default function PaginaCadastro() {
  return (
    <main className="grid flex-1 bg-white text-[#172033] lg:grid-cols-[minmax(0,4fr)_minmax(0,5fr)]">
      <section aria-labelledby="titulo-cadastro" className="mx-auto flex w-full max-w-lg flex-col justify-center px-6 py-10 sm:px-12 lg:py-12">
        <Link href="/login" aria-label="Fast Attendance — ir para o login"
          className="mb-9 flex w-fit items-center gap-3 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#5040e8]">
          <span className="flex size-10 items-center justify-center rounded-xl bg-[#5040e8] text-white" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-6">
              <path d="M5 4h14v13H9l-4 3V4Z" strokeLinejoin="round" />
              <path d="m9 10 2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
          <span><span className="block text-lg font-bold tracking-tight">Fast Attendance</span>
            <span className="block text-xs text-[#64748b]">Gestão de presença</span></span>
        </Link>
        <header>
          <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[#5040e8]">Área do professor</p>
          <h1 id="titulo-cadastro" className="text-2xl font-bold tracking-tight">Crie sua conta docente</h1>
          <p className="mt-1 text-sm leading-relaxed text-[#64748b]">Cadastre-se para gerenciar suas turmas e presenças.</p>
        </header>
        <FormularioCadastro />
        <p className="mt-7 text-sm text-[#64748b]">Já tem cadastro?{" "}
          <Link href="/login" className="font-semibold text-[#5040e8] underline-offset-4 hover:underline">Acesse sua conta</Link>
        </p>
        <p className="mt-4 text-sm text-[#64748b]">É aluno?{" "}
          {/* Outra aplicação: link comum para não receber o basePath deste app. */}
          <a href="/aluno/login" className="font-medium text-[#5040e8] underline-offset-4 hover:underline">
            Acesse a área do aluno
          </a>
        </p>
      </section>
      <aside aria-labelledby="titulo-beneficios" className="flex flex-col justify-between gap-16 bg-[#172033] px-6 py-10 text-white sm:px-12 lg:px-14 lg:py-16">
        <div className="mx-auto w-full max-w-lg">
          <h2 id="titulo-beneficios" className="text-2xl font-semibold tracking-tight sm:text-3xl">Por que usar o Fast Attendance?</h2>
          <ul className="mt-9 space-y-7">
            {beneficios.map(([titulo, descricao], indice) => (
              <li key={titulo} className="flex items-start gap-4">
                <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-white/10">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="size-5">
                    {indice === 0 ? <path d="M5 5h14v15H5V5Zm3-3v6m8-6v6M5 10h14m-11 5 2 2 5-4" />
                      : indice === 1 ? <path d="m4 16 6-6 4 4 6-8m-6 0h6v6" />
                      : <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M15 3.13a4 4 0 0 1 0 7.75M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z" />}
                  </svg>
                </span>
                <div><h3 className="font-medium">{titulo}</h3>
                  <p className="mt-1 text-sm leading-relaxed text-slate-400">{descricao}</p></div>
              </li>
            ))}
          </ul>
        </div>
        <p className="mx-auto w-full max-w-lg text-xs leading-relaxed text-slate-400">Mais tempo para aprender. Mais facilidade para acompanhar cada presença.</p>
      </aside>
    </main>
  );
}
