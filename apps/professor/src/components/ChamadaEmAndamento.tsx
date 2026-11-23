/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { esquecerChamada, guardarChamada, guardarUltimaSala, lerChamada, type ChamadaGuardada } from "@/lib/chamadas";
import type { ModoChamada, SessaoEncerrada, SessaoModoAlterado } from "@/lib/protocolo";
import { useRecurso } from "@/lib/recurso";
import type { Turma } from "@/lib/tipos";
import { aplicarModo } from "@/lib/trocaDeModo";
import { useChamadaClassica } from "@/lib/useChamadaClassica";
import { useAcompanharSessao } from "@/lib/useAcompanharSessao";
import { descreverConexao, useClienteSessao } from "@/lib/useClienteSessao";
import { ErroSessao } from "@/lib/ws";
import { AreaAutenticada } from "./AreaAutenticada";
import { ChamadaQrCode } from "./ChamadaQrCode";
import { ChamadaSom } from "./ChamadaSom";
import { ListaDeChamada } from "./ListaDeChamada";
import { TrocaDeModo, type ResultadoDaTroca } from "./TrocaDeModo";
import { Botao } from "./ui/Botao";
import { MensagemDeErro } from "./ui/MensagemDeErro";
import { botaoPrincipal } from "./ui/estilos";

const NOME_DO_MODO: Record<ModoChamada, string> = { CLASSICA: "Chamada clássica", QRCODE: "QR Code", SOM: "Som" };

const hora = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });

/** Chamada aberta: cabeçalho com contador e encerrar; a lista da clássica (P6), o QR (P7) ou o som (P8). */
export function ChamadaEmAndamento() {
  const { id, sessaoId } = useParams<{ id: string; sessaoId: string }>();
  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const { cliente, estado } = useClienteSessao();
  const [confirmando, setConfirmando] = useState(false);
  const [encerrando, setEncerrando] = useState(false);
  const [encerrada, setEncerrada] = useState<SessaoEncerrada | null>(null);
  const [encerradaSemResumo, setEncerradaSemResumo] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const aoVivo = useAcompanharSessao(cliente, sessaoId);
  const classica = useChamadaClassica(sessaoId, id, cliente, aoVivo, (mensagem) => {
    esquecerChamada(sessaoId);
    setEncerradaSemResumo(mensagem);
  });
  // O acompanhar respondeu que a sessão acabou ou não existe (ex.: encerrada por tempo com a aba fechada)
  const fimSemResumo = encerradaSemResumo ?? aoVivo.encerrada;

  useEffect(() => {
    if (aoVivo.encerrada) esquecerChamada(sessaoId);
  }, [aoVivo.encerrada, sessaoId]);

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

  /**
   * Chamada guardada com o modo segundo o servidor: a resposta da troca, o evento de outra aba ou o
   * estado depois de uma reconexão. A tela usa este valor, e não só o do navegador, para trocar de modo
   * na hora; a gravação no navegador vem logo depois, no efeito abaixo.
   */
  function comModoDoServidor(guardada: ChamadaGuardada | null): ChamadaGuardada | null {
    if (!guardada || !aoVivo.modo) return guardada;
    const salaId = aoVivo.salaId === undefined ? (guardada.salaId ?? null) : aoVivo.salaId;
    if (aoVivo.modo === guardada.modo && (salaId ?? undefined) === guardada.salaId) return guardada;
    return aplicarModo(guardada, { modo: aoVivo.modo, salaId });
  }

  // Guarda o modo novo no navegador, para recarregar a página já no modo certo
  useEffect(() => {
    const guardada = lerChamada(sessaoId);
    if (!guardada || !aoVivo.modo) return;
    const atual = comModoDoServidor(guardada);
    if (atual && atual !== guardada) guardarChamada(atual);
    // comModoDoServidor só depende do que está nesta lista
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [aoVivo.modo, aoVivo.salaId, sessaoId]);

  /** Pede a troca de modo ao servidor (#49); a tela muda pela resposta, sem esperar o evento. */
  async function trocarModo(destino: ModoChamada, salaId?: string): Promise<ResultadoDaTroca> {
    try {
      const resposta = await cliente.requisitar<SessaoModoAlterado>("sessao.trocar_modo", {
        sessaoId,
        modo: destino,
        ...(salaId ? { salaId } : {}),
      });
      aoVivo.registrarTroca(resposta);
      if (resposta.salaId) guardarUltimaSala(resposta.salaId);
      return { ok: true };
    } catch (e) {
      if (e instanceof ErroSessao && e.codigo === "SESSAO_ENCERRADA") {
        esquecerChamada(sessaoId);
        setEncerradaSemResumo(e.message);
        return { ok: true };
      }
      return { ok: false, erro: e instanceof ErroSessao ? e.message : "Erro inesperado. Tente novamente." };
    }
  }

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
        const chamada = comModoDoServidor(lerChamada(sessaoId));
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

        if (fimSemResumo || !chamada) {
          return (
            <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-4 p-6">
              <h1 className="text-xl font-semibold">{fimSemResumo ?? "Chamada não encontrada neste navegador"}</h1>
              {!fimSemResumo && (
                <p className="text-sm text-suave">Ela pode ter sido encerrada ou aberta em outro navegador ou aparelho.</p>
              )}
              {voltar}
            </main>
          );
        }

        const eClassica = chamada.modo === "CLASSICA";
        const eQr = chamada.modo === "QRCODE";
        const eSom = chamada.modo === "SOM";
        // Na QR e no som os totais vêm do servidor; na clássica, das marcações do servidor entre os alunos da
        // lista (com o toque ainda sem resposta), para o contador bater com o que está marcado
        const contagem = eClassica
          ? classica.alunos.situacao === "pronto" && aoVivo.carregado
            ? { presentes: classica.presentes, total: classica.total }
            : null
          : aoVivo.totalMatriculados !== null
            ? { presentes: aoVivo.totalPresentes, total: aoVivo.totalMatriculados }
            : null;
        // A key reinicia a confirmação quando o modo muda (por esta aba ou por outra)
        const troca = (
          <TrocaDeModo
            key={chamada.modo}
            modoAtual={chamada.modo}
            salaDaChamada={chamada.salaId}
            conectado={estado === "pronto"}
            trocar={trocarModo}
          />
        );

        return (
          <main className={`mx-auto flex w-full flex-1 flex-col gap-4 px-6 pb-6 ${eQr || eSom ? "max-w-5xl" : "max-w-3xl"}`}>
            <div className="sticky top-0 z-10 -mx-6 flex flex-col gap-3 border-b border-borda-suave bg-background px-6 pt-6 pb-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm text-suave">
                    {NOME_DO_MODO[chamada.modo]} · aberta às {hora.format(chamada.abertaEm)}
                  </p>
                  <h1 className="text-xl font-semibold">{titulo}</h1>
                </div>
                <div className="flex items-center gap-4">
                  {contagem && (
                    <p aria-live="polite" className="text-suave">
                      <span className="text-2xl font-bold tabular-nums text-foreground">{contagem.presentes}</span>/{contagem.total}{" "}
                      presentes
                    </p>
                  )}
                  <Botao variante="perigo-contorno" onClick={() => setConfirmando(true)} disabled={confirmando}>
                    Encerrar
                  </Botao>
                </div>
              </div>
              <p role="status" className="flex items-center gap-2 text-sm text-suave">
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
                  <p className="text-sm text-suave">
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
              <MensagemDeErro>{erro ?? classica.erro ?? aoVivo.erro}</MensagemDeErro>
            </div>

            {eClassica && (
              <ListaDeChamada
                turmaId={id}
                alunos={classica.alunos}
                marcacoes={classica.marcacoes}
                // Até o estado chegar não se sabe quem está presente: um toque poderia inverter errado
                habilitada={estado === "pronto" && aoVivo.carregado}
                emLote={classica.emLote}
                alternar={classica.alternar}
                marcarLote={classica.marcarLote}
              />
            )}
            {eQr && (
              <ChamadaQrCode
                chamada={chamada}
                cliente={cliente}
                presentes={aoVivo.presentes}
                carregado={aoVivo.carregado}
                troca={troca}
              />
            )}
            {eSom && (
              <ChamadaSom
                chamada={chamada}
                cliente={cliente}
                presentes={aoVivo.presentes}
                carregado={aoVivo.carregado}
                troca={troca}
              />
            )}
          </main>
        );
      }}
    </AreaAutenticada>
  );
}
