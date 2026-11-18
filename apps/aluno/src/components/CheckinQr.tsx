"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  ErroCheckin,
  enviarCheckinQr,
  interpretarQr,
  obterLocalizacao,
} from "@/lib/checkin";
import { iniciarLeituraQr, mensagemDaCamera } from "@/lib/leitorQr";
import { obterDispositivoId } from "@/lib/dispositivo";
import { ErroSessao } from "@/lib/ws";
import { descreverConexao, useClienteSessao } from "@/lib/useClienteSessao";
import type { CheckinOk } from "@/lib/protocolo";
import type { TurmaDoAluno } from "@/lib/tipos";

type Etapa =
  "pronto" | "localizando" | "lendo" | "enviando" | "erro" | "sucesso";
const botao =
  "min-h-11 rounded-lg bg-[#3b2db5] px-5 py-3 text-sm font-semibold text-white hover:bg-[#2f2491] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca disabled:cursor-not-allowed disabled:opacity-50";
const semNovaTentativa = new Set([
  "SESSAO_ENCERRADA",
  "SESSAO_NAO_ENCONTRADA",
  "ALUNO_NAO_MATRICULADO",
  "PERMISSAO_NEGADA",
  "MODO_INCOMPATIVEL",
  "PRESENCA_DUPLICADA",
  "DISPOSITIVO_JA_UTILIZADO",
  "VERSAO_NAO_SUPORTADA",
]);

export function CheckinQr({ turma }: { turma: TurmaDoAluno }) {
  const { cliente, estado } = useClienteSessao();
  const [etapa, setEtapa] = useState<Etapa>("pronto");
  const [falha, setFalha] = useState<{
    codigo: string;
    mensagem: string;
  } | null>(null);
  const [resultado, setResultado] = useState<CheckinOk | null>(null);
  const [precisao, setPrecisao] = useState<number | null>(null);
  const video = useRef<HTMLVideoElement>(null);
  const operacao = useRef<AbortController | null>(null);
  const ocupado = useRef(false);
  const emAndamento =
    etapa === "localizando" || etapa === "lendo" || etapa === "enviando";
  useEffect(() => {
    const cancelarAoOcultar = () => {
      if (document.visibilityState === "hidden") operacao.current?.abort();
    };
    document.addEventListener("visibilitychange", cancelarAoOcultar);
    return () => {
      operacao.current?.abort();
      document.removeEventListener("visibilitychange", cancelarAoOcultar);
    };
  }, []);

  function exibirErro(erro: unknown, controle: AbortController) {
    if (controle.signal.aborted) return;
    controle.abort("finalizado");
    ocupado.current = false;
    setFalha(
      erro instanceof ErroCheckin || erro instanceof ErroSessao
        ? { codigo: erro.codigo, mensagem: erro.message }
        : {
            codigo: "ERRO_INTERNO",
            mensagem:
              "Não foi possível confirmar sua presença. Tente novamente ou procure o professor.",
          },
    );
    setEtapa("erro");
  }

  async function iniciar() {
    if (ocupado.current || estado !== "pronto" || !video.current) return;
    ocupado.current = true;
    operacao.current?.abort();
    const controle = new AbortController();
    operacao.current = controle;
    controle.signal.addEventListener(
      "abort",
      () => {
        if (controle.signal.reason === "finalizado") return;
        ocupado.current = false;
        setFalha({
          codigo: "OPERACAO_INTERROMPIDA",
          mensagem:
            "A leitura foi interrompida ao sair desta tela ou ocultar a aba. Se a presença já estava sendo enviada, confira seu histórico antes de tentar novamente.",
        });
        setEtapa("erro");
      },
      { once: true },
    );
    setFalha(null);
    setResultado(null);
    setPrecisao(null);
    setEtapa("localizando");
    try {
      const local = await obterLocalizacao(controle.signal);
      if (controle.signal.aborted) return;
      setPrecisao(local.precisaoMetros);
      setEtapa("lendo");
      await iniciarLeituraQr(
        video.current,
        controle.signal,
        (texto) => {
          void (async () => {
            if (controle.signal.aborted) return;
            setEtapa("enviando");
            try {
              const qr = interpretarQr(texto, turma.chamadaAberta!.id);
              const atual =
                Date.now() - local.obtidaEm > 15000
                  ? await obterLocalizacao(controle.signal)
                  : local;
              if (controle.signal.aborted) return;
              let dispositivo: string;
              try {
                dispositivo = obterDispositivoId();
              } catch {
                throw new ErroCheckin(
                  "ARMAZENAMENTO_BLOQUEADO",
                  "Permita o armazenamento deste site para identificar seu aparelho e tente novamente.",
                );
              }
              const confirmado = await enviarCheckinQr(
                cliente,
                qr,
                atual,
                dispositivo,
              );
              if (controle.signal.aborted) return;
              setResultado(confirmado);
              setEtapa("sucesso");
              ocupado.current = false;
              controle.abort("finalizado");
            } catch (erro) {
              exibirErro(erro, controle);
            }
          })();
        },
        (erro) => exibirErro(erro, controle),
      );
    } catch (erro) {
      exibirErro(
        erro instanceof ErroCheckin ? erro : mensagemDaCamera(erro),
        controle,
      );
    }
  }

  function cancelar() {
    operacao.current?.abort("finalizado");
    ocupado.current = false;
    setEtapa("pronto");
    setFalha(null);
    setPrecisao(null);
  }

  // O efeito da rota é limpo pelo <Activity>. Uma nova tentativa sempre exige gesto e QR novo.
  const podeTentar = !semNovaTentativa.has(falha?.codigo ?? "");
  return (
    <section
      className="mt-6 rounded-2xl border border-borda bg-superficie p-5 sm:p-7"
      aria-label="Leitura do QR Code"
    >
      <h1 className="text-2xl font-bold">
        {etapa === "sucesso" ? "Presença confirmada" : "Confirmar presença"}
      </h1>
      <p className="mt-2 text-sm text-suave">
        {turma.disciplina} · {turma.nome}
      </p>
      {etapa === "sucesso" && resultado ? (
        <div
          role="status"
          className="mt-6 rounded-xl border border-sucesso-borda bg-sucesso-fundo p-5 text-sucesso-texto"
        >
          <p className="font-semibold">
            Sua presença foi registrada por QR Code.
          </p>
          <p className="mt-2 text-sm">
            Confirmada pelo servidor em{" "}
            {new Date(resultado.registradoEm).toLocaleString("pt-BR")}.
          </p>
        </div>
      ) : (
        <>
          <div
            hidden={etapa === "erro"}
            className="relative mt-6 overflow-hidden rounded-xl bg-[#172033]"
          >
            <video
              ref={video}
              autoPlay
              playsInline
              muted
              aria-label="Câmera para leitura do QR Code"
              className={`aspect-[4/3] w-full object-cover ${etapa !== "lendo" ? "opacity-20" : ""}`}
            />
            {etapa !== "lendo" && (
              <p className="absolute inset-0 flex items-center justify-center px-6 text-center text-sm text-white">
                {etapa === "localizando"
                  ? "Obtendo sua localização..."
                  : etapa === "enviando"
                    ? "Confirmando com o servidor..."
                    : "Inicie a leitura para abrir a câmera."}
              </p>
            )}
          </div>
          {falha && (
            <div
              role="alert"
              className="mt-5 rounded-xl border border-perigo-borda bg-perigo-fundo p-4 text-sm leading-relaxed text-perigo-texto"
            >
              <p className="font-semibold">
                {falha.codigo === "PRESENCA_DUPLICADA"
                  ? "Presença já registrada"
                  : "Não foi possível confirmar"}
              </p>
              <p className="mt-1">{falha.mensagem}</p>
            </div>
          )}
          <p
            role="status"
            aria-live="polite"
            className="mt-4 text-sm text-suave"
          >
            {etapa === "lendo"
              ? "Aponte para o QR Code atual exibido pelo professor."
              : descreverConexao(estado)}
          </p>
          {precisao !== null && !falha && (
            <p className="mt-2 text-xs text-suave">
              Localização obtida · precisão aproximada de {Math.round(precisao)}{" "}
              m.
            </p>
          )}
          <p className="mt-3 text-xs leading-relaxed text-suave">
            A câmera é processada neste aparelho. As coordenadas são enviadas ao
            servidor para verificar a proximidade da sala.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            {emAndamento ? (
              <button
                type="button"
                onClick={cancelar}
                disabled={etapa === "enviando"}
                className="min-h-11 rounded-lg border border-borda-forte px-5 py-3 text-sm font-semibold disabled:opacity-50"
              >
                {etapa === "enviando"
                  ? "Confirmando presença..."
                  : "Cancelar leitura"}
              </button>
            ) : (
              podeTentar && (
                <button
                  type="button"
                  onClick={() => void iniciar()}
                  disabled={estado !== "pronto"}
                  className={botao}
                >
                  {etapa === "erro"
                    ? "Ler QR Code novamente"
                    : "Iniciar leitura do QR Code"}
                </button>
              )
            )}
            {emAndamento && (
              <span className="sr-only" role="status">
                {etapa === "localizando"
                  ? "Obtendo localização"
                  : etapa === "enviando"
                    ? "Enviando presença"
                    : "Lendo QR Code"}
              </span>
            )}
          </div>
        </>
      )}
      <Link
        href={`/historico?turmaId=${encodeURIComponent(turma.id)}`}
        className="mt-6 mr-5 inline-flex min-h-11 items-center text-sm font-semibold text-marca hover:underline"
      >
        Ver histórico desta turma
      </Link>
      <Link
        href="/"
        className="mt-6 inline-flex min-h-11 items-center text-sm font-semibold text-marca hover:underline"
      >
        Voltar ao início
      </Link>
    </section>
  );
}
