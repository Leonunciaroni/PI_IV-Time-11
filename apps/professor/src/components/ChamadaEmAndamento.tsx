"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { esquecerChamada, lerChamada } from "@/lib/chamadas";
import type { ModoChamada, SessaoEncerrada } from "@/lib/protocolo";
import { useRecurso } from "@/lib/recurso";
import type { Turma } from "@/lib/tipos";
import { useChamadaClassica } from "@/lib/useChamadaClassica";
import { descreverConexao, useClienteSessao } from "@/lib/useClienteSessao";
import { usePresencasAoVivo } from "@/lib/usePresencasAoVivo";
import { ErroSessao } from "@/lib/ws";
import { AreaAutenticada } from "./AreaAutenticada";
import { ChamadaQrCode } from "./ChamadaQrCode";
import { ListaDeChamada } from "./ListaDeChamada";
import { Botao } from "./ui/Botao";
import { MensagemDeErro } from "./ui/MensagemDeErro";
import { botaoPrincipal } from "./ui/estilos";

const NOME_DO_MODO: Record<ModoChamada, string> = { CLASSICA: "Chamada clássica", QRCODE: "QR Code", SOM: "Som" };

const hora = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });

/** Chamada aberta: cabeçalho com contador e encerrar; a lista da clássica (P6). O QR (P7) entra aqui. */
export function ChamadaEmAndamento() {
  const { id, sessaoId } = useParams<{ id: string; sessaoId: string }>();
  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const { cliente, estado } = useClienteSessao();
  const [confirmando, setConfirmando] = useState(false);
  const [encerrando, setEncerrando] = useState(false);
  const [encerrada, setEncerrada] = useState<SessaoEncerrada | null>(null);
  const [encerradaSemResumo, setEncerradaSemResumo] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const classica = useChamadaClassica(sessaoId, id, cliente, (mensagem) => {
    esquecerChamada(sessaoId);
    setEncerradaSemResumo(mensagem);
  });
  const aoVivo = usePresencasAoVivo(cliente, sessaoId);

  // O servidor avisa todos os conectados quando a sessão termina (inclusive por tempo limite)
  useEffect(
    () =>
      cliente.aoEvento<SessaoEncerrada>("sessao.encerrada", (dados) => {
        if (dados.sessaoId !== sessaoId) return;
        esquecerChamada(sessaoId);
        setEncerrada(dados);
      }),
    [cliente, sessaoId],
  );

  async function encerrar() {
    setErro(null);
    setEncerrando(true);
    try {
      const resposta = await cliente.requisitar<SessaoEncerrada>("sessao.encerrar", { sessaoId });
      esquecerChamada(sessaoId);
      setEncerrada(resposta);
    } catch (e) {
      if (e instanceof ErroSessao && e.codigo === "SESSAO_ENCERRADA") {
        esquecerChamada(sessaoId);
        setEncerradaSemResumo(e.message);
      } else {
        setErro(e instanceof ErroSessao ? e.message : "Erro inesperado. Tente novamente.");
      }
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
        const voltar = (
          <Link href={`/turmas/${id}`} className={`${botaoPrincipal} self-start px-4 py-2`}>
            Voltar para a turma
          </Link>
        );

        if (encerrada) {
          return (
            <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-4 p-6">
              <p className="text-sm text-sucesso">
                {encerrada.motivo === "TEMPO_LIMITE" ? "Chamada encerrada por tempo limite às " : "Chamada encerrada às "}
                {hora.format(encerrada.encerradaEm)}
              </p>
              <h1 className="text-xl font-semibold">{titulo}</h1>
              <p>
                <span className="text-2xl font-bold tabular-nums">{encerrada.totalPresentes}</span>{" "}
                {encerrada.totalPresentes === 1 ? "aluno presente" : "alunos presentes"}
              </p>
              {voltar}
            </main>
          );
        }

        if (encerradaSemResumo || !chamada) {
          return (
            <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-4 p-6">
              <h1 className="text-xl font-semibold">
                {encerradaSemResumo ?? "Chamada não encontrada neste navegador"}
              </h1>
              {!encerradaSemResumo && (
                <p className="text-sm opacity-70">Ela pode ter sido encerrada ou aberta em outro navegador ou aparelho.</p>
              )}
              {voltar}
            </main>
          );
        }

        const eClassica = chamada.modo === "CLASSICA";
        const eQr = chamada.modo === "QRCODE";
        const contagem =
          classica.alunos.situacao !== "pronto"
            ? null
            : { presentes: eClassica ? classica.presentes : aoVivo.length, total: classica.total };

        return (
          <main className={`mx-auto flex w-full flex-1 flex-col gap-4 px-6 pb-6 ${eQr ? "max-w-5xl" : "max-w-3xl"}`}>
            <div className="sticky top-0 z-10 -mx-6 flex flex-col gap-3 border-b border-borda-suave bg-background px-6 pt-6 pb-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm opacity-70">
                    {NOME_DO_MODO[chamada.modo]} · aberta às {hora.format(chamada.abertaEm)}
                  </p>
                  <h1 className="text-xl font-semibold">{titulo}</h1>
                </div>
                <div className="flex items-center gap-4">
                  {contagem && (
                    <p aria-live="polite" className="opacity-80">
                      <span className="text-2xl font-bold tabular-nums">{contagem.presentes}</span>/{contagem.total}{" "}
                      presentes
                    </p>
                  )}
                  <Botao variante="perigo-contorno" onClick={() => setConfirmando(true)} disabled={confirmando}>
                    Encerrar
                  </Botao>
                </div>
              </div>
              <p role="status" className="flex items-center gap-2 text-sm opacity-70">
                <span
                  aria-hidden="true"
                  className={`size-2 rounded-full ${estado === "pronto" ? "bg-sucesso" : "bg-borda-forte"}`}
                />
                {descreverConexao(estado)}
              </p>

              {confirmando && (
                <section aria-labelledby="titulo-encerrar" className="flex flex-col gap-3 rounded-cartao border border-perigo p-4">
                  <h2 id="titulo-encerrar" className="font-semibold">
                    Encerrar a chamada?
                  </h2>
                  <p className="text-sm opacity-70">
                    {contagem ? `${contagem.presentes} de ${contagem.total} alunos estão presentes. ` : ""}
                    Depois de encerrar, nenhuma presença é registrada.
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Botao variante="perigo" onClick={encerrar} disabled={encerrando || estado !== "pronto"}>
                      {encerrando ? "Encerrando..." : "Encerrar chamada"}
                    </Botao>
                    <Botao variante="secundario" onClick={() => setConfirmando(false)} disabled={encerrando}>
                      Continuar a chamada
                    </Botao>
                  </div>
                </section>
              )}
              <MensagemDeErro>{erro ?? classica.erro}</MensagemDeErro>
            </div>

            {eClassica && (
              <ListaDeChamada
                turmaId={id}
                alunos={classica.alunos}
                marcacoes={classica.marcacoes}
                habilitada={estado === "pronto"}
                emLote={classica.emLote}
                alternar={classica.alternar}
                marcarLote={classica.marcarLote}
              />
            )}
            {eQr && <ChamadaQrCode chamada={chamada} cliente={cliente} presentes={aoVivo} />}
          </main>
        );
      }}
    </AreaAutenticada>
  );
}
