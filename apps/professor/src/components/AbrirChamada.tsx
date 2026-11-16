"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { chamadaDaTurma, guardarChamada, guardarUltimaSala, ultimaSala } from "@/lib/chamadas";
import type { ModoChamada, SessaoAberta, SessaoAbrir } from "@/lib/protocolo";
import { useRecurso } from "@/lib/recurso";
import type { Sala } from "@/lib/sala";
import type { Turma } from "@/lib/tipos";
import { descreverConexao, useClienteSessao } from "@/lib/useClienteSessao";
import { ErroSessao } from "@/lib/ws";
import { AreaAutenticada } from "./AreaAutenticada";
import { Botao } from "./ui/Botao";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { MensagemDeErro } from "./ui/MensagemDeErro";
import { botaoSecundario } from "./ui/estilos";

const MODOS: { modo: ModoChamada; nome: string; descricao: string; disponivel: boolean }[] = [
  { modo: "CLASSICA", nome: "Clássica", descricao: "Marque a lista aluno por aluno ou todos de uma vez.", disponivel: true },
  { modo: "QRCODE", nome: "QR Code", descricao: "Código que muda a cada 15 s, com a localização do aluno.", disponivel: true },
  { modo: "SOM", nome: "Som", descricao: "Ultrassom, sem câmera nem foto.", disponivel: true },
];

export function AbrirChamada() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const salas = useRecurso<Sala[]>("/api/salas");
  const { cliente, estado } = useClienteSessao();
  const [modo, setModo] = useState<ModoChamada>("CLASSICA");
  const [salaEscolhida, setSalaEscolhida] = useState<string | null>(null);
  const [abrindo, setAbrindo] = useState(false);
  const [erro, setErro] = useState<{ mensagem: string; jaAberta: boolean } | null>(null);

  return (
    <AreaAutenticada>
      {() => {
        const listaSalas = salas.situacao === "pronto" ? salas.dados : [];
        // Sem escolha explícita, vale a última sala usada neste navegador ou a primeira da lista
        const lembrada = ultimaSala();
        const salaId =
          salaEscolhida ?? (listaSalas.some((s) => s.id === lembrada) ? lembrada : (listaSalas[0]?.id ?? null));
        const precisaSala = modo === "QRCODE";
        const semSala = precisaSala && salas.situacao === "pronto" && listaSalas.length === 0;
        const retomar = erro?.jaAberta ? chamadaDaTurma(id) : null;
        const podeAbrir = estado === "pronto" && !abrindo && (!precisaSala || salaId !== null);

        async function abrir() {
          setErro(null);
          setAbrindo(true);
          try {
            const pedido: SessaoAbrir = { turmaId: id, modo, ...(precisaSala && salaId ? { salaId } : {}) };
            const aberta = await cliente.requisitar<SessaoAberta>("sessao.abrir", pedido);
            guardarChamada({ ...aberta, ...(pedido.salaId ? { salaId: pedido.salaId } : {}) });
            if (pedido.salaId) guardarUltimaSala(pedido.salaId);
            router.push(`/turmas/${id}/chamada/${aberta.sessaoId}`);
          } catch (e) {
            if (e instanceof ErroSessao) setErro({ mensagem: e.message, jaAberta: e.codigo === "SESSAO_JA_ABERTA" });
            else setErro({ mensagem: "Erro inesperado. Tente novamente.", jaAberta: false });
          } finally {
            // A rota fica preservada ao navegar; sem isto o botão voltaria travado em "Abrindo..."
            setAbrindo(false);
          }
        }

        return (
          <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <Link href={`/turmas/${id}`} className="text-sm underline">
                  ← {turma.situacao === "pronto" ? `${turma.dados.disciplina} · ${turma.dados.nome}` : "Turma"}
                </Link>
                <h1 className="mt-2 text-xl font-semibold">Abrir chamada</h1>
              </div>
              <p role="status" className="flex items-center gap-2 text-sm opacity-70">
                <span
                  aria-hidden="true"
                  className={`size-2 rounded-full ${estado === "pronto" ? "bg-sucesso" : "bg-borda-forte"}`}
                />
                {descreverConexao(estado)}
              </p>
            </div>

            {turma.situacao === "carregando" && <Carregando texto="Carregando turma..." />}
            {turma.situacao === "erro" && (
              <ErroDeCarga mensagem={turma.erro.message} aoTentarNovamente={turma.recarregar} />
            )}

            {turma.situacao === "pronto" && (
              <>
                <fieldset className="flex flex-col gap-3">
                  <legend className="mb-3 font-semibold">Como será a chamada de hoje?</legend>
                  <div className="grid gap-3 sm:grid-cols-3">
                    {MODOS.map((opcao) => {
                      const escolhido = modo === opcao.modo;
                      return (
                        <label
                          key={opcao.modo}
                          className={`relative flex flex-col gap-1 rounded-cartao border p-4 ${
                            !opcao.disponivel
                              ? "cursor-not-allowed border-dashed border-borda-forte opacity-60"
                              : escolhido
                                ? "cursor-pointer border-primaria bg-realce ring-1 ring-primaria"
                                : "cursor-pointer border-borda"
                          }`}
                        >
                          <input
                            type="radio"
                            name="modo"
                            value={opcao.modo}
                            checked={escolhido}
                            disabled={!opcao.disponivel}
                            onChange={() => {
                              setModo(opcao.modo);
                              setErro(null);
                            }}
                            className="absolute top-4 right-4 size-4 accent-primaria"
                          />
                          <span className="font-semibold">
                            {opcao.nome}
                            {!opcao.disponivel && (
                              <span className="ml-2 rounded-full border border-borda-forte px-2 text-xs font-medium">
                                Em breve
                              </span>
                            )}
                          </span>
                          <span className="pr-6 text-sm opacity-70">{opcao.descricao}</span>
                        </label>
                      );
                    })}
                  </div>
                </fieldset>

                {precisaSala && (
                  <div className="flex flex-col gap-2">
                    {salas.situacao === "carregando" && <Carregando texto="Carregando salas..." />}
                    {salas.situacao === "erro" && (
                      <ErroDeCarga mensagem={salas.erro.message} aoTentarNovamente={salas.recarregar} />
                    )}
                    {semSala && (
                      <p className="text-sm">
                        O modo QR Code precisa de uma sala cadastrada.{" "}
                        <Link href="/salas" className="underline">
                          Cadastrar sala
                        </Link>
                      </p>
                    )}
                    {salas.situacao === "pronto" && listaSalas.length > 0 && (
                      <div className="flex flex-wrap items-end gap-3">
                        <label className="flex min-w-0 flex-1 flex-col gap-1 text-sm font-medium">
                          Sala
                          <select
                            value={salaId ?? ""}
                            onChange={(e) => setSalaEscolhida(e.target.value)}
                            className="rounded-controle border border-borda-forte bg-background px-3 py-2 text-base outline-none focus:border-primaria"
                          >
                            {listaSalas.map((sala) => (
                              <option key={sala.id} value={sala.id}>
                                {sala.nome}
                              </option>
                            ))}
                          </select>
                        </label>
                        <Link href="/salas" className={`${botaoSecundario} px-3 py-2 text-sm`}>
                          Cadastrar sala
                        </Link>
                      </div>
                    )}
                  </div>
                )}

                <div className="flex flex-col items-center gap-2">
                  <div className="flex w-full max-w-xs flex-col">
                    <Botao onClick={abrir} disabled={!podeAbrir}>
                      {abrindo ? "Abrindo..." : "Abrir chamada"}
                    </Botao>
                  </div>
                  {retomar && (
                    <Link href={`/turmas/${id}/chamada/${retomar.sessaoId}`} className={`${botaoSecundario} px-3 py-2 text-sm`}>
                      Retomar a chamada aberta
                    </Link>
                  )}
                  <MensagemDeErro>{erro?.mensagem}</MensagemDeErro>
                  {erro?.jaAberta && !retomar && (
                    // O servidor ainda não informa qual é a sessão aberta (#30), então só dá para retomar neste navegador
                    <p className="max-w-md text-center text-sm opacity-70">
                      Ela foi aberta em outro navegador ou aparelho. Encerre por lá, ou aguarde: a chamada se encerra
                      sozinha depois de 4 horas.
                    </p>
                  )}
                </div>
              </>
            )}
          </main>
        );
      }}
    </AreaAutenticada>
  );
}
