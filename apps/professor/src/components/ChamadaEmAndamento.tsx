"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { esquecerChamada, lerChamada } from "@/lib/chamadas";
import type { ModoChamada } from "@/lib/protocolo";
import { useRecurso } from "@/lib/recurso";
import type { Turma } from "@/lib/tipos";
import { descreverConexao, useClienteSessao } from "@/lib/useClienteSessao";
import { ErroSessao } from "@/lib/ws";
import { AreaAutenticada } from "./AreaAutenticada";
import { Botao } from "./ui/Botao";
import { MensagemDeErro } from "./ui/MensagemDeErro";
import { botaoPrincipal } from "./ui/estilos";

const NOME_DO_MODO: Record<ModoChamada, string> = { CLASSICA: "Chamada clássica", QRCODE: "QR Code", SOM: "Som" };

const hora = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });

interface SessaoEncerrada {
  totalPresentes: number;
  encerradaEm: number;
}

/** Chamada aberta: por enquanto, o resumo e a opção de encerrar. A marcação (P6) e o QR (P7) entram aqui. */
export function ChamadaEmAndamento() {
  const { id, sessaoId } = useParams<{ id: string; sessaoId: string }>();
  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const { cliente, estado } = useClienteSessao();
  const [confirmando, setConfirmando] = useState(false);
  const [encerrando, setEncerrando] = useState(false);
  const [encerrada, setEncerrada] = useState<SessaoEncerrada | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  async function encerrar() {
    setErro(null);
    setEncerrando(true);
    try {
      const resposta = await cliente.requisitar<SessaoEncerrada>("sessao.encerrar", { sessaoId });
      esquecerChamada(sessaoId);
      setEncerrada(resposta);
    } catch (e) {
      if (e instanceof ErroSessao && e.codigo === "SESSAO_ENCERRADA") esquecerChamada(sessaoId);
      setErro(e instanceof ErroSessao ? e.message : "Erro inesperado. Tente novamente.");
    } finally {
      setEncerrando(false);
      setConfirmando(false);
    }
  }

  return (
    <AreaAutenticada>
      {() => {
        const chamada = lerChamada(sessaoId);
        const titulo = turma.situacao === "pronto" ? `${turma.dados.disciplina} · ${turma.dados.nome}` : "Turma";

        if (encerrada) {
          return (
            <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-4 p-6">
              <p className="text-sm text-sucesso">Chamada encerrada às {hora.format(encerrada.encerradaEm)}</p>
              <h1 className="text-xl font-semibold">{titulo}</h1>
              <p>
                <span className="text-2xl font-bold tabular-nums">{encerrada.totalPresentes}</span>{" "}
                {encerrada.totalPresentes === 1 ? "aluno presente" : "alunos presentes"}
              </p>
              <Link href={`/turmas/${id}`} className={`${botaoPrincipal} self-start px-4 py-2`}>
                Voltar para a turma
              </Link>
            </main>
          );
        }

        if (!chamada) {
          return (
            <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-4 p-6">
              <h1 className="text-xl font-semibold">Chamada não encontrada neste navegador</h1>
              <p className="text-sm opacity-70">
                Ela pode ter sido encerrada ou aberta em outro navegador ou aparelho.
              </p>
              <Link href={`/turmas/${id}`} className="text-sm underline">
                ← Voltar para a turma
              </Link>
            </main>
          );
        }

        return (
          <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="text-sm opacity-70">
                  {NOME_DO_MODO[chamada.modo]} · aberta às {hora.format(chamada.abertaEm)}
                </p>
                <h1 className="text-xl font-semibold">{titulo}</h1>
              </div>
              <p role="status" className="flex items-center gap-2 text-sm opacity-70">
                <span
                  aria-hidden="true"
                  className={`size-2 rounded-full ${estado === "pronto" ? "bg-sucesso" : "bg-borda-forte"}`}
                />
                {descreverConexao(estado)}
              </p>
            </div>

            {!confirmando ? (
              <div>
                <Botao variante="secundario" onClick={() => setConfirmando(true)}>
                  Encerrar chamada
                </Botao>
              </div>
            ) : (
              <section aria-labelledby="titulo-encerrar" className="flex flex-col gap-3 rounded-cartao border border-perigo p-4">
                <h2 id="titulo-encerrar" className="font-semibold">
                  Encerrar a chamada?
                </h2>
                <p className="text-sm opacity-70">Depois de encerrar, nenhuma presença é registrada.</p>
                <div className="flex flex-wrap gap-2">
                  <Botao onClick={encerrar} disabled={encerrando || estado !== "pronto"}>
                    {encerrando ? "Encerrando..." : "Encerrar chamada"}
                  </Botao>
                  <Botao variante="secundario" onClick={() => setConfirmando(false)} disabled={encerrando}>
                    Continuar a chamada
                  </Botao>
                </div>
              </section>
            )}
            <MensagemDeErro>{erro}</MensagemDeErro>
          </main>
        );
      }}
    </AreaAutenticada>
  );
}
