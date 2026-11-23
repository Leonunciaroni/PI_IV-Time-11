/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
"use client";

import { useCallback, useMemo, type ReactNode } from "react";
import type { ChamadaGuardada } from "@/lib/chamadas";
import type { PresenteAoVivo } from "@/lib/presencas";
import { desenharQr } from "@/lib/qr";
import { conteudoQr } from "@/lib/totp";
import { useCodigoRotativo } from "@/lib/useCodigoRotativo";
import type { ClienteSessao } from "@/lib/ws";
import { PainelAoVivo } from "./PainelAoVivo";
import { MensagemDeErro } from "./ui/MensagemDeErro";

interface ChamadaQrCodeProps {
  chamada: ChamadaGuardada;
  cliente: ClienteSessao;
  /** Presentes segundo o servidor (estado e eventos), do mais recente para o mais antigo. */
  presentes: PresenteAoVivo[];
  /** O estado da sessão já chegou do servidor. */
  carregado: boolean;
  /** Botões "Trocar para…" (#49), embaixo da lista de presentes. */
  troca?: ReactNode;
}

/** QR Code rotativo (P7): projetado para a turma, com o tempo até o próximo código. */
export function ChamadaQrCode({ chamada, cliente, presentes, carregado, troca }: ChamadaQrCodeProps) {
  const diferenca = useCallback(() => cliente.diferencaRelogioMs, [cliente]);
  const { token, restanteMs, periodoMs, erro } = useCodigoRotativo(chamada.totp, diferenca);
  const desenho = useMemo(() => (token ? desenharQr(conteudoQr(chamada.sessaoId, token)) : null), [chamada.sessaoId, token]);
  const segundos = Math.max(1, Math.ceil(restanteMs / 1000));

  return (
    <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
      <section aria-label="QR Code da chamada" className="flex min-w-0 flex-1 flex-col items-center gap-4">
        {/* Sempre preto sobre branco, inclusive no tema escuro: QR invertido falha em muitos leitores */}
        <div className="aspect-square w-full max-w-[min(30rem,60vh)] rounded-cartao bg-white p-4">
          {desenho ? (
            <svg
              viewBox={`0 0 ${desenho.tamanho} ${desenho.tamanho}`}
              role="img"
              aria-label="QR Code para os alunos registrarem presença"
              className="size-full"
              shapeRendering="crispEdges"
            >
              <path d={desenho.caminho} fill="#000" />
            </svg>
          ) : (
            <p role="status" className="flex size-full items-center justify-center text-center text-sm text-black/70">
              {erro ?? "Gerando o código..."}
            </p>
          )}
        </div>

        <div className="flex w-full max-w-[min(30rem,60vh)] flex-col gap-2">
          <div className="flex justify-between text-lg">
            <span>Novo código em</span>
            <span className="font-bold tabular-nums">{segundos} s</span>
          </div>
          <div
            role="progressbar"
            aria-label="Tempo até o próximo código"
            aria-valuemin={0}
            aria-valuemax={periodoMs / 1000}
            aria-valuenow={segundos}
            className="h-2.5 overflow-hidden rounded-full bg-borda"
          >
            <div className="h-full rounded-full bg-primaria" style={{ width: `${(restanteMs / periodoMs) * 100}%` }} />
          </div>
        </div>
        <p className="text-center text-suave">Aponte a câmera do app do aluno para o código.</p>
        <MensagemDeErro>{desenho ? erro : null}</MensagemDeErro>
      </section>

      <PainelAoVivo presentes={presentes} carregado={carregado} vazio="Nenhum aluno registrou presença ainda.">
        {troca}
      </PainelAoVivo>
    </div>
  );
}
