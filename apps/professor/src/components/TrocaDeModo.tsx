/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
"use client";

import Link from "next/link";
import { useState } from "react";
import { ultimaSala } from "@/lib/chamadas";
import type { ModoChamada } from "@/lib/protocolo";
import { useRecurso } from "@/lib/recurso";
import type { Sala } from "@/lib/sala";
import { explicacaoDaTroca, precisaDeSala, rotuloDaTroca, salaSugerida, trocasPermitidas } from "@/lib/trocaDeModo";
import { Botao } from "./ui/Botao";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { MensagemDeErro } from "./ui/MensagemDeErro";

/** Resultado de uma troca: deu certo, ou a mensagem para mostrar. */
export type ResultadoDaTroca = { ok: true } | { ok: false; erro: string };

interface TrocaDeModoProps {
  modoAtual: ModoChamada;
  /** Sala que a chamada já tem (de uma abertura ou troca anterior para QR Code). */
  salaDaChamada?: string;
  /** Conexão pronta: sem ela, o pedido não chega ao servidor. */
  conectado: boolean;
  trocar: (destino: ModoChamada, salaId?: string) => Promise<ResultadoDaTroca>;
}

/**
 * Botões "Trocar para…" da chamada aberta (#49), cada um com uma confirmação curta: um toque sem querer
 * não tira a turma do QR Code nem do som. Ir para o QR Code pede a sala na própria confirmação.
 */
export function TrocaDeModo({ modoAtual, salaDaChamada, conectado, trocar }: TrocaDeModoProps) {
  const [destino, setDestino] = useState<ModoChamada | null>(null);
  const [trocando, setTrocando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const opcoes = trocasPermitidas(modoAtual);
  if (opcoes.length === 0) return null;

  async function confirmar(salaId?: string) {
    if (!destino) return;
    setErro(null);
    setTrocando(true);
    const resultado = await trocar(destino, salaId);
    setTrocando(false);
    if (resultado.ok) setDestino(null);
    else setErro(resultado.erro);
  }

  const confirmacao = {
    conectado,
    trocando,
    aoConfirmar: confirmar,
    aoCancelar: () => setDestino(null),
  };

  return (
    <div className="flex flex-col gap-2">
      {destino === null ? (
        opcoes.map((opcao) => (
          <Botao
            key={opcao}
            variante="secundario"
            onClick={() => {
              setErro(null);
              setDestino(opcao);
            }}
            disabled={!conectado}
          >
            {rotuloDaTroca(opcao)}
          </Botao>
        ))
      ) : (
        <section
          aria-labelledby="titulo-troca"
          className="flex flex-col gap-3 rounded-cartao border border-borda-forte bg-superficie p-4"
        >
          <h3 id="titulo-troca" className="font-semibold">
            {rotuloDaTroca(destino)}?
          </h3>
          <p className="text-sm text-suave">{explicacaoDaTroca(destino)}</p>
          {precisaDeSala(destino) ? (
            <ConfirmacaoComSala salaDaChamada={salaDaChamada} {...confirmacao} />
          ) : (
            <BotoesDeConfirmacao {...confirmacao} pronto />
          )}
        </section>
      )}
      <MensagemDeErro>{erro}</MensagemDeErro>
      {!conectado && <p className="text-sm text-suave">A troca fica disponível quando a conexão voltar.</p>}
    </div>
  );
}

interface ConfirmacaoProps {
  conectado: boolean;
  trocando: boolean;
  aoConfirmar: (salaId?: string) => void;
  aoCancelar: () => void;
}

function BotoesDeConfirmacao({
  conectado,
  trocando,
  aoConfirmar,
  aoCancelar,
  pronto,
  salaId,
}: ConfirmacaoProps & { pronto: boolean; salaId?: string }) {
  return (
    <div className="flex flex-wrap gap-2">
      <Botao onClick={() => aoConfirmar(salaId)} disabled={trocando || !conectado || !pronto}>
        {trocando ? "Trocando..." : "Confirmar troca"}
      </Botao>
      <Botao variante="secundario" onClick={aoCancelar} disabled={trocando}>
        Cancelar
      </Botao>
    </div>
  );
}

/**
 * Confirmação da troca para o QR Code: escolhe a sala (sugere a da chamada, a última usada ou a
 * primeira) e só libera o botão quando há uma sala.
 */
function ConfirmacaoComSala({ salaDaChamada, ...confirmacao }: ConfirmacaoProps & { salaDaChamada?: string }) {
  const salas = useRecurso<Sala[]>("/api/salas");
  const [escolhida, setEscolhida] = useState<string | null>(null);

  if (salas.situacao === "carregando") {
    return (
      <>
        <Carregando texto="Carregando salas..." />
        <BotoesDeConfirmacao {...confirmacao} pronto={false} />
      </>
    );
  }
  if (salas.situacao === "erro") {
    return (
      <>
        <ErroDeCarga mensagem={salas.erro.message} aoTentarNovamente={salas.recarregar} />
        <BotoesDeConfirmacao {...confirmacao} pronto={false} />
      </>
    );
  }
  if (salas.dados.length === 0) {
    return (
      <>
        <p className="text-sm text-suave">
          Nenhuma sala cadastrada. O QR Code confere a distância até a sala:{" "}
          <Link href="/salas" className="underline">
            cadastre uma sala
          </Link>{" "}
          antes de trocar.
        </p>
        <BotoesDeConfirmacao {...confirmacao} pronto={false} />
      </>
    );
  }

  // A sugestão vale até o professor escolher outra
  const salaId = escolhida ?? salaSugerida(salas.dados.map((s) => s.id), salaDaChamada, ultimaSala());

  return (
    <>
      <label className="flex flex-col gap-1 text-sm font-medium">
        Sala
        <select
          value={salaId ?? ""}
          onChange={(e) => setEscolhida(e.target.value)}
          className="rounded-controle border border-borda-forte bg-superficie px-3 py-2 text-base outline-none focus:border-primaria"
        >
          {salas.dados.map((sala) => (
            <option key={sala.id} value={sala.id}>
              {sala.nome}
            </option>
          ))}
        </select>
      </label>
      <BotoesDeConfirmacao {...confirmacao} pronto={salaId !== null} salaId={salaId ?? undefined} />
    </>
  );
}
