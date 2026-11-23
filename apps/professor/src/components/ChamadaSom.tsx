/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
"use client";

import { useCallback, type ReactNode } from "react";
import type { ChamadaGuardada } from "@/lib/chamadas";
import type { PresenteAoVivo } from "@/lib/presencas";
import { useEmissorSom, type SituacaoDoSom } from "@/lib/useEmissorSom";
import type { ClienteSessao } from "@/lib/ws";
import { PainelAoVivo } from "./PainelAoVivo";
import { Botao } from "./ui/Botao";
import { MensagemDeErro } from "./ui/MensagemDeErro";

const ESTADO: Record<SituacaoDoSom, string> = {
  aguardando: "Toque em Iniciar som para começar",
  emitindo: "Transmitindo 18–20 kHz",
  pausado: "Som pausado",
};

interface ChamadaSomProps {
  chamada: ChamadaGuardada;
  cliente: ClienteSessao;
  presentes: PresenteAoVivo[];
  carregado: boolean;
  /** Botões "Trocar para…" (#49), embaixo da lista de presentes. */
  troca?: ReactNode;
}

/** Emissão do sinal sonoro (P8): o notebook toca o código e o app do aluno capta pelo microfone. */
export function ChamadaSom({ chamada, cliente, presentes, carregado, troca }: ChamadaSomProps) {
  const diferenca = useCallback(() => cliente.diferencaRelogioMs, [cliente]);
  const { situacao, volume, erro, iniciar, pausar, mudarVolume } = useEmissorSom(chamada.totp, diferenca);
  const emitindo = situacao === "emitindo";

  return (
    <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
      <section aria-label="Sinal sonoro da chamada" className="flex min-w-0 flex-1 flex-col items-center gap-5 py-4">
        <svg
          viewBox="0 0 120 64"
          aria-hidden="true"
          className={`h-20 w-36 ${emitindo ? "text-primaria" : "opacity-40"}`}
          fill="none"
          stroke="currentColor"
          strokeWidth="4"
          strokeLinecap="round"
        >
          <circle cx="60" cy="32" r="7" fill="currentColor" stroke="none" />
          <path d="M44 18a20 20 0 0 0 0 28M76 18a20 20 0 0 1 0 28" />
          <path d="M32 8a34 34 0 0 0 0 48M88 8a34 34 0 0 1 0 48" className={emitindo ? "animate-pulse" : ""} />
          <path d="M20 0a48 48 0 0 0 0 64M100 0a48 48 0 0 1 0 64" className={emitindo ? "animate-pulse" : ""} />
        </svg>

        <p role="status" className="text-lg font-semibold">
          {ESTADO[situacao]}
        </p>

        <label className="flex w-full max-w-xs flex-col gap-2 text-sm font-medium">
          <span className="flex justify-between">
            Volume
            <span className="tabular-nums text-suave">{Math.round(volume * 100)}%</span>
          </span>
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={Math.round(volume * 100)}
            onChange={(e) => mudarVolume(Number(e.target.value) / 100)}
            className="accent-primaria dark:accent-auto"
          />
        </label>

        <div className="flex w-full max-w-xs flex-col">
          {emitindo ? (
            <Botao variante="secundario" onClick={pausar}>
              Pausar
            </Botao>
          ) : (
            <Botao onClick={iniciar}>{situacao === "pausado" ? "Retomar som" : "Iniciar som"}</Botao>
          )}
        </div>

        <p className="max-w-sm text-center text-sm text-suave">
          Deixe esta aba aberta, com o notebook voltado para a turma. Os alunos registram presença pelo
          microfone do app, sem câmera. Um novo código sai a cada 15 s.
        </p>
        <MensagemDeErro>{erro}</MensagemDeErro>
      </section>

      <PainelAoVivo presentes={presentes} carregado={carregado} vazio="Nenhum aluno registrou presença ainda.">
        {troca}
      </PainelAoVivo>
    </div>
  );
}
