"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ErroApi } from "@/lib/api";
import { baixarArquivo } from "@/lib/arquivos";
import { useRecurso } from "@/lib/recurso";
import type { Aluno, Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { AvisoDeSucesso } from "./ui/AvisoDeSucesso";
import { Botao } from "./ui/Botao";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { MensagemDeErro } from "./ui/MensagemDeErro";
import { botaoPrincipal, botaoSecundario } from "./ui/estilos";

/** Texto da confirmação que a matrícula manda pela URL (?matriculados=N&ja=M). */
function confirmacaoMatricula(matriculados: string | null, ja: string | null): string | null {
  if (matriculados === null) return null;
  const novos = Number(matriculados);
  const repetidos = Number(ja ?? 0);
  // URL editada à mão: sem números válidos, não mostra nada
  if (!Number.isInteger(novos) || !Number.isInteger(repetidos)) return null;
  const partes = [novos === 1 ? "1 aluno matriculado" : `${novos} alunos matriculados`];
  if (repetidos > 0) partes.push(repetidos === 1 ? "1 já estava na turma" : `${repetidos} já estavam na turma`);
  return `${partes.join("; ")}.`;
}

export function DetalhesTurma() {
  const { id } = useParams<{ id: string }>();
  const parametros = useSearchParams();
  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const alunos = useRecurso<Aluno[]>(`/api/turmas/${id}/alunos`);
  const confirmacao = confirmacaoMatricula(parametros.get("matriculados"), parametros.get("ja"));
  const [exportando, setExportando] = useState(false);
  const [erroExportacao, setErroExportacao] = useState<string | null>(null);

  /** Consolidado de frequência (presenças, aulas e % por aluno), pronto para o Excel. */
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
          <div>
            <Link href="/" className="text-sm underline">
              ← Minhas turmas
            </Link>
            {turma.situacao === "pronto" && (
              <>
                <h1 className="mt-2 text-xl font-semibold break-words">
                  {turma.dados.disciplina} · {turma.dados.nome}
                </h1>
                <p className="text-sm opacity-70">Horário padrão: {turma.dados.horarioPadrao}</p>
              </>
            )}
          </div>

          {turma.situacao === "carregando" && <Carregando texto="Carregando turma..." />}
          {/* Turma inexistente ou de outro professor: tentar de novo não adianta, então só oferece a volta */}
          {turma.situacao === "erro" && turma.erro.status === 404 && (
            <div className="flex flex-col items-start gap-3">
              <p role="alert" className="text-sm text-perigo">
                Turma não encontrada. Ela pode ter sido removida ou pertencer a outro professor.
              </p>
              <Link href="/" className={`${botaoSecundario} px-3 py-2 text-sm`}>
                Voltar para Minhas turmas
              </Link>
            </div>
          )}
          {turma.situacao === "erro" && turma.erro.status !== 404 && (
            <ErroDeCarga mensagem={turma.erro.message} aoTentarNovamente={turma.recarregar} />
          )}

          {turma.situacao === "pronto" && (
            <>
              {confirmacao && <AvisoDeSucesso>{confirmacao}</AvisoDeSucesso>}

              <div className="flex flex-wrap gap-2 text-sm">
                <Link href={`/turmas/${id}/chamada`} className={`${botaoPrincipal} px-3 py-2`}>
                  Abrir chamada
                </Link>
                <Link href={`/turmas/${id}/matricula`} className={`${botaoSecundario} px-3 py-2`}>
                  Matricular alunos
                </Link>
                <Botao variante="secundario" onClick={exportar} disabled={exportando}>
                  {exportando ? "Gerando arquivo..." : "Exportar CSV"}
                </Botao>
              </div>
              {erroExportacao && (
                <div className="-mt-4">
                  <MensagemDeErro>{erroExportacao}</MensagemDeErro>
                </div>
              )}

              <section aria-labelledby="titulo-alunos" className="flex flex-col gap-3">
                <h2 id="titulo-alunos" className="font-semibold">
                  Alunos matriculados
                  {alunos.situacao === "pronto" && alunos.dados.length > 0 && (
                    <span className="font-normal opacity-70"> ({alunos.dados.length})</span>
                  )}
                </h2>

                {alunos.situacao === "carregando" && <Carregando texto="Carregando alunos..." />}
                {alunos.situacao === "erro" && (
                  <ErroDeCarga mensagem={alunos.erro.message} aoTentarNovamente={alunos.recarregar} />
                )}
                {alunos.situacao === "pronto" &&
                  (alunos.dados.length === 0 ? (
                    <p className="text-sm opacity-70">
                      Nenhum aluno matriculado ainda. Use “Matricular alunos” para incluir os alunos da turma.
                    </p>
                  ) : (
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="border-b border-borda-forte">
                          <th scope="col" className="py-2 pr-3 font-semibold">
                            Aluno
                          </th>
                          <th scope="col" className="w-32 py-2 font-semibold">
                            Matrícula
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {alunos.dados.map((aluno) => (
                          <tr key={aluno.id} className="border-b border-borda-suave">
                            <td className="py-2 pr-3 break-words">{aluno.nome}</td>
                            <td className="py-2 tabular-nums">{aluno.matricula}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  ))}
              </section>
            </>
          )}
        </main>
      )}
    </AreaAutenticada>
  );
}
