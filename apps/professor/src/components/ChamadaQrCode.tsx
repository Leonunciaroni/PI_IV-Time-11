"use client";

import { useCallback, useMemo } from "react";
import type { ChamadaGuardada } from "@/lib/chamadas";
import type { PresencaAtualizada } from "@/lib/protocolo";
import { desenharQr } from "@/lib/qr";
import { conteudoQr } from "@/lib/totp";
import { useCodigoRotativo } from "@/lib/useCodigoRotativo";
import type { ClienteSessao } from "@/lib/ws";
import { Botao } from "./ui/Botao";
import { MensagemDeErro } from "./ui/MensagemDeErro";

const hora = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });

interface ChamadaQrCodeProps {
  chamada: ChamadaGuardada;
  cliente: ClienteSessao;
  /** Presenças recebidas do servidor nesta tela, da mais recente para a mais antiga. */
  presentes: PresencaAtualizada[];
}

/** QR Code rotativo (P7): projetado para a turma, com o tempo até o próximo código. */
export function ChamadaQrCode({ chamada, cliente, presentes }: ChamadaQrCodeProps) {
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
        <p className="text-center opacity-70">Aponte a câmera do app do aluno para o código.</p>
        <MensagemDeErro>{desenho ? erro : null}</MensagemDeErro>
      </section>

      <aside aria-labelledby="titulo-presentes" className="flex w-full flex-col gap-3 lg:w-80">
        <h2 id="titulo-presentes" className="flex items-center gap-2 font-semibold">
          <span aria-hidden="true" className="size-2 rounded-full bg-sucesso" />
          Presentes (ao vivo)
        </h2>
        {presentes.length === 0 ? (
          <p className="text-sm opacity-70">Os alunos aparecem aqui conforme registram presença pelo código.</p>
        ) : (
          <ol className="border-t border-borda-suave">
            {presentes.map((presenca) => (
              <li key={presenca.alunoId} className="flex gap-3 border-b border-borda-suave py-2">
                <span className="font-mono text-sm opacity-70">{hora.format(presenca.registradoEm)}</span>
                <span>{presenca.nomeAluno}</span>
              </li>
            ))}
          </ol>
        )}
        {/* O protocolo v1 ainda não tem troca de modo (#49); encerrar e abrir outra criaria duas aulas */}
        <Botao variante="secundario" disabled>
          Trocar para clássica
        </Botao>
        <p className="-mt-1 text-sm opacity-70">A troca de modo ainda não está disponível.</p>
      </aside>
    </div>
  );
}
