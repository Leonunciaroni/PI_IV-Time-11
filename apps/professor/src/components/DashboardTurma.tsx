/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { ErroApi } from "@/lib/api";
import { baixarArquivo } from "@/lib/arquivos";
import {
  alturaDaBarra,
  aulaAbaixoDoMinimo,
  dataDaAula,
  descricaoDaAula,
  formatarPercentual,
  MODOS,
  rotuloDoModo,
} from "@/lib/dashboard";
import { useRecurso } from "@/lib/recurso";
import type { EstatisticasTurma, Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { Botao } from "./ui/Botao";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { MensagemDeErro } from "./ui/MensagemDeErro";
import { botaoSecundario, cartao } from "./ui/estilos";

/** Tela P9: frequência por aula, alunos abaixo do mínimo e uso de cada modo de chamada. */
export function DashboardTurma() {
  const { id } = useParams<{ id: string }>();
  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const estatisticas = useRecurso<EstatisticasTurma>(`/api/turmas/${id}/estatisticas`);
  const [exportando, setExportando] = useState(false);
  const [erroExportacao, setErroExportacao] = useState<string | null>(null);

  async function exportar() {
    setErroExportacao(null);
    setExportando(true);
    try {
      await baixarArquivo(`/api/turmas/${id}/exportacao/consolidado`, "frequencia.csv");
    } catch (e) {
      setErroExportacao(e instanceof ErroApi ? e.message : "Não foi possível gerar o arquivo. Tente novamente.");
    } finally {
      setExportando(false);
    }
  }

  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <Link href={`/turmas/${id}`} className="text-sm underline">
                ← {turma.situacao === "pronto" ? turma.dados.nome : "Turma"}
              </Link>
              <h1 className="mt-2 text-xl font-semibold break-words">
                Frequência
                {turma.situacao === "pronto" && (
                  <span className="font-normal opacity-70"> · {turma.dados.disciplina}</span>
                )}
              </h1>
            </div>
            {estatisticas.situacao === "pronto" && (
              <Botao variante="secundario" onClick={exportar} disabled={exportando}>
                {exportando ? "Gerando arquivo..." : "Exportar CSV"}
              </Botao>
            )}
          </div>
          {erroExportacao && (
            <div className="-mt-4">
              <MensagemDeErro>{erroExportacao}</MensagemDeErro>
            </div>
          )}

          {estatisticas.situacao === "carregando" && <Carregando texto="Carregando frequência..." />}
          {estatisticas.situacao === "erro" && estatisticas.erro.status === 404 && (
            <div className="flex flex-col items-start gap-3">
              <p role="alert" className="text-sm text-perigo">
                Turma não encontrada. Ela pode ter sido removida ou pertencer a outro professor.
              </p>
              <Link href="/" className={`${botaoSecundario} px-3 py-2 text-sm`}>
                Voltar para Minhas turmas
              </Link>
            </div>
          )}
          {estatisticas.situacao === "erro" && estatisticas.erro.status !== 404 && (
            <ErroDeCarga mensagem={estatisticas.erro.message} aoTentarNovamente={estatisticas.recarregar} />
          )}
          {estatisticas.situacao === "pronto" && <Painel dados={estatisticas.dados} />}
        </main>
      )}
    </AreaAutenticada>
  );
}

function Painel({ dados }: { dados: EstatisticasTurma }) {
  return (
    <>
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Numero rotulo="Aulas dadas" valor={String(dados.aulasDadas)} />
        <Numero rotulo="Frequência média" valor={dados.aulasDadas === 0 ? "—" : formatarPercentual(dados.frequenciaMedia)} />
        <Numero rotulo={`Abaixo de ${dados.minimo}%`} valor={String(dados.abaixoDoMinimo.length)} />
      </dl>

      {dados.aulasDadas === 0 ? (
        <p className="text-sm opacity-70">
          Nenhuma aula encerrada ainda. A frequência aparece aqui depois que a primeira chamada for encerrada.
        </p>
      ) : (
        <>
          <GraficoPorAula dados={dados} />

          <section aria-labelledby="titulo-modos" className="flex flex-col gap-2">
            <h2 id="titulo-modos" className="font-semibold">
              Uso por modo
            </h2>
            <ul className="flex flex-wrap gap-2 text-sm">
              {MODOS.map((modo) => (
                <li key={modo} className={`${cartao} px-3 py-2`}>
                  {rotuloDoModo(modo)} <span className="font-semibold tabular-nums">{dados.usoPorModo[modo] ?? 0}</span>
                </li>
              ))}
            </ul>
          </section>

          <section aria-labelledby="titulo-abaixo" className="flex flex-col gap-2">
            <h2 id="titulo-abaixo" className="font-semibold">
              Alunos abaixo do mínimo
            </h2>
            {dados.abaixoDoMinimo.length === 0 ? (
              <p className="text-sm opacity-70">Todos os alunos estão com {dados.minimo}% ou mais.</p>
            ) : (
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-borda-forte">
                    <th scope="col" className="py-2 pr-3 font-semibold">
                      Aluno
                    </th>
                    <th scope="col" className="w-28 py-2 pr-3 font-semibold">
                      Matrícula
                    </th>
                    <th scope="col" className="w-24 py-2 text-right font-semibold">
                      Frequência
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {dados.abaixoDoMinimo.map((aluno) => (
                    <tr key={aluno.alunoId} className="border-b border-borda-suave">
                      <td className="py-2 pr-3 break-words">{aluno.nome}</td>
                      <td className="py-2 pr-3 tabular-nums">{aluno.matricula}</td>
                      <td className="py-2 text-right tabular-nums text-perigo">
                        {formatarPercentual(aluno.percentual)}
                        <span className="sr-only">
                          {" "}
                          ({aluno.presencas} de {aluno.aulas} aulas)
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </>
  );
}

function Numero({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className={`${cartao} px-4 py-3`}>
      <dt className="text-sm opacity-70">{rotulo}</dt>
      <dd className="text-2xl font-semibold tabular-nums">{valor}</dd>
    </div>
  );
}

/** Barras verticais em CSS; a aula abaixo do mínimo fica com a cor de perigo. */
function GraficoPorAula({ dados }: { dados: EstatisticasTurma }) {
  return (
    <section aria-labelledby="titulo-grafico" className="flex flex-col gap-2">
      <h2 id="titulo-grafico" className="font-semibold">
        Frequência por aula
      </h2>
      <div className="flex gap-2">
        <div aria-hidden="true" className="flex h-40 flex-col justify-between text-xs tabular-nums opacity-70">
          <span>100%</span>
          <span>0%</span>
        </div>
        <ol className="flex h-40 flex-1 items-end gap-1 overflow-x-auto border-b border-l border-borda-forte px-1">
          {dados.aulas.map((aula) => {
            const abaixo = aulaAbaixoDoMinimo(aula, dados.minimo);
            return (
              <li
                key={aula.sessaoId}
                title={descricaoDaAula(aula)}
                className="flex h-full min-w-4 flex-1 flex-col justify-end"
              >
                <span className="sr-only">{descricaoDaAula(aula)}</span>
                <span
                  aria-hidden="true"
                  className={`block w-full rounded-t-sm ${abaixo ? "bg-perigo" : "bg-primaria"}`}
                  style={{ height: `${alturaDaBarra(aula.percentual)}%` }}
                />
              </li>
            );
          })}
        </ol>
      </div>
      <div aria-hidden="true" className="flex justify-between pl-10 text-xs opacity-70">
        <span>{dataDaAula(dados.aulas[0].data)}</span>
        {dados.aulas.length > 1 && <span>{dataDaAula(dados.aulas[dados.aulas.length - 1].data)}</span>}
      </div>
      <p className="text-xs opacity-70">
        Em vermelho, as aulas com frequência abaixo de {dados.minimo}%. Passe o mouse sobre uma barra para ver os números.
      </p>
    </section>
  );
}
