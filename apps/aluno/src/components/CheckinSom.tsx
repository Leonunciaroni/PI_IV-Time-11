// Guilherme Preventi Correia
// RA: 25001694

"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ErroApi } from "@/lib/api";
import { carregarReceptor, escutarToken, type FabricaReceptor } from "@/lib/audio/receptor";
import { ErroCheckin } from "@/lib/checkin";
import { enviarCheckinSom, mensagemDoSom, verificarChamadaSom } from "@/lib/checkinSom";
import { obterDispositivoId } from "@/lib/dispositivo";
import { encerrarSessao } from "@/lib/sessao";
import { descreverConexao, useClienteSessao } from "@/lib/useClienteSessao";
import type { CheckinOk } from "@/lib/protocolo";
import type { TurmaDoAluno } from "@/lib/tipos";

type Etapa = "pronto" | "preparando" | "escutando" | "enviando" | "erro" | "sucesso";
const terminal = new Set(["CHAMADA_INDISPONIVEL", "SESSAO_ENCERRADA", "SESSAO_NAO_ENCONTRADA", "ALUNO_NAO_MATRICULADO", "PERMISSAO_NEGADA", "MODO_INCOMPATIVEL", "PRESENCA_DUPLICADA", "DISPOSITIVO_JA_UTILIZADO", "VERSAO_NAO_SUPORTADA"]);
const botao = "min-h-11 rounded-lg bg-[#3b2db5] px-5 py-3 text-sm font-semibold text-white hover:bg-[#2f2491] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca disabled:cursor-not-allowed disabled:opacity-50";

export function CheckinSom({ turma, token }: { turma: TurmaDoAluno; token: string }) {
  const { cliente, estado } = useClienteSessao();
  const [fabrica, setFabrica] = useState<FabricaReceptor | null>(null);
  const [falhaLeitor, setFalhaLeitor] = useState(false);
  const [etapa, setEtapa] = useState<Etapa>("pronto");
  const [falha, setFalha] = useState<{ codigo: string; mensagem: string } | null>(null);
  const [resultado, setResultado] = useState<CheckinOk | null>(null);
  const fase = useRef<Etapa>("pronto");
  const operacao = useRef<AbortController | null>(null);
  const ocupado = useRef(false);

  useEffect(() => {
    let ativo = true;
    // Carrega o módulo antes do toque; iniciar() mantém o gesto exigido pelo AudioContext.
    carregarReceptor().then((modulo) => { if (ativo) setFabrica(modulo); })
      .catch(() => { if (ativo) setFalhaLeitor(true); });
    return () => { ativo = false; };
  }, []);

  useEffect(() => {
    const ocultar = () => {
      if (document.visibilityState === "hidden") operacao.current?.abort("oculto");
    };
    const remover = cliente.aoMudarEstado((conexao) => {
      if (conexao !== "pronto" && fase.current !== "enviando") operacao.current?.abort("conexao");
    });
    document.addEventListener("visibilitychange", ocultar);
    return () => {
      operacao.current?.abort("sair");
      remover();
      document.removeEventListener("visibilitychange", ocultar);
    };
  }, [cliente]);

  function mudar(etapa: Etapa) { fase.current = etapa; setEtapa(etapa); }

  async function iniciar() {
    if (!fabrica || ocupado.current || estado !== "pronto") return;
    ocupado.current = true;
    const controle = new AbortController();
    operacao.current = controle;
    controle.signal.addEventListener("abort", () => {
      if (controle.signal.reason === "finalizado") return;
      ocupado.current = false;
      if (controle.signal.reason === "cancelado") return;
      setFalha({
        codigo: "OPERACAO_INTERROMPIDA",
        mensagem: fase.current === "enviando"
          ? "A confirmação foi interrompida. Confira o histórico depois que o professor encerrar a chamada antes de tentar novamente."
          : "A escuta foi interrompida ao sair da tela, ocultar a aba ou perder a conexão. Inicie novamente quando estiver pronto.",
      });
      mudar("erro");
    }, { once: true });
    setFalha(null);
    setResultado(null);
    mudar("preparando");
    try {
      const recebido = await escutarToken(fabrica, controle.signal, () => mudar("escutando"));
      if (controle.signal.aborted) return;
      mudar("enviando");
      // O som só contém o token. Sessão e matrícula são verificadas pela API autenticada.
      const sessaoId = turma.chamadaAberta!.id;
      await verificarChamadaSom(token, turma.id, sessaoId, controle.signal);
      if (controle.signal.aborted) return;
      let dispositivoId: string;
      try { dispositivoId = obterDispositivoId(); }
      catch { throw new ErroCheckin("ARMAZENAMENTO_BLOQUEADO", "Permita o armazenamento deste site para identificar seu aparelho e tente novamente."); }
      const resposta = await enviarCheckinSom(cliente, sessaoId, recebido, dispositivoId);
      if (controle.signal.aborted) return;
      setResultado(resposta);
      mudar("sucesso");
    } catch (erro) {
      if (controle.signal.aborted) return;
      if (erro instanceof ErroApi && erro.status === 401) { encerrarSessao(); return; }
      setFalha(erro instanceof ErroApi ? { codigo: "FALHA_CONSULTA", mensagem: erro.message } : mensagemDoSom(erro));
      mudar("erro");
    } finally {
      controle.abort("finalizado");
      if (operacao.current === controle) ocupado.current = false;
    }
  }

  function cancelar() {
    operacao.current?.abort("cancelado");
    ocupado.current = false;
    setFalha(null);
    mudar("pronto");
  }

  const emAndamento = etapa === "preparando" || etapa === "escutando" || etapa === "enviando";
  const texto: Record<Etapa, string> = {
    pronto: "Toque para ativar o microfone",
    preparando: "Abrindo o microfone...",
    escutando: "Ouvindo o sinal...",
    enviando: "Confirmando com o servidor...",
    erro: "A escuta foi encerrada",
    sucesso: "Presença confirmada",
  };
  return (
    <section aria-label="Check-in por som" className="mt-6 rounded-xl border border-borda bg-superficie p-5 sm:p-7">
      <h1 className="text-2xl font-bold">{etapa === "sucesso" ? "Presença confirmada" : "Confirmar presença por som"}</h1>
      <p className="mt-2 text-sm text-suave">{turma.disciplina} ({turma.nome})</p>
      {resultado && etapa === "sucesso" ? (
        <div role="status" className="mt-6 rounded-lg border border-sucesso-borda bg-sucesso-fundo p-5 text-sucesso-texto">
          <p className="font-semibold">Sua presença foi registrada por som.</p>
          <p className="mt-2 text-sm">Confirmada pelo servidor em {new Date(resultado.registradoEm).toLocaleString("pt-BR")}.</p>
        </div>
      ) : (
        <>
          <div className="mt-6 rounded-xl bg-[#172033] px-5 py-8 text-center text-white">
            <svg aria-hidden="true" viewBox="0 0 64 64" fill="none" stroke="currentColor" strokeWidth="3" className={`mx-auto size-16 ${etapa === "escutando" ? "animate-pulse motion-reduce:animate-none" : "opacity-60"}`}>
              <rect x="25" y="8" width="14" height="30" rx="7" /><path d="M18 29v3a14 14 0 0 0 28 0v-3M32 46v10M24 56h16M10 22v16M54 22v16" />
            </svg>
            <p role="status" className="mt-4 font-semibold">{texto[etapa]}</p>
            <p className="mt-2 text-sm leading-relaxed text-slate-300">Mantenha o aparelho desbloqueado e esta tela aberta, próximo ao som do professor.</p>
          </div>
          {falha && <div role="alert" className="mt-5 rounded-lg border border-perigo-borda bg-perigo-fundo p-4 text-sm leading-relaxed text-perigo-texto">
            <p className="font-semibold">{falha.codigo === "PRESENCA_DUPLICADA" ? "Presença já registrada" : "Não foi possível confirmar"}</p>
            <p className="mt-1">{falha.mensagem}</p>
          </div>}
          {falhaLeitor && <div role="alert" className="mt-5 rounded-lg border border-perigo-borda bg-perigo-fundo p-4 text-sm text-perigo-texto">
            <p>Não foi possível carregar o leitor de som. Verifique sua conexão e recarregue a página.</p>
            <button type="button" onClick={() => window.location.reload()} className={`${botao} mt-3`}>Recarregar página</button>
          </div>}
          <p role="status" className="mt-4 text-sm text-suave">{descreverConexao(estado)}</p>
          <p className="mt-3 text-xs leading-relaxed text-suave">O áudio é processado neste aparelho e não é gravado nem enviado ao servidor. Somente o código recebido e a identificação da chamada e do aparelho são enviados para confirmar a presença.</p>
          <div className="mt-6">
            {emAndamento ? <button type="button" onClick={cancelar} disabled={etapa === "enviando"} className="min-h-11 rounded-lg border border-borda-forte px-5 py-3 text-sm font-semibold disabled:opacity-50">
              {etapa === "enviando" ? "Confirmando presença..." : "Cancelar escuta"}
            </button> : !terminal.has(falha?.codigo ?? "") && <button type="button" onClick={() => void iniciar()} disabled={!fabrica || falhaLeitor || estado !== "pronto"} className={botao}>
              {!fabrica && !falhaLeitor ? "Carregando leitor de som..." : etapa === "erro" ? "Escutar novamente" : "Iniciar escuta"}
            </button>}
          </div>
        </>
      )}
      <div className="mt-6 flex flex-wrap gap-x-5 gap-y-2 text-sm font-semibold text-marca">
        <Link href={`/historico?turmaId=${encodeURIComponent(turma.id)}`} className="inline-flex min-h-11 items-center hover:underline">Ver histórico desta turma</Link>
        <Link href="/" className="inline-flex min-h-11 items-center hover:underline">Voltar ao início</Link>
      </div>
      <p className="mt-3 text-xs text-suave">O histórico mostra as presenças após o professor encerrar a chamada.</p>
    </section>
  );
}
