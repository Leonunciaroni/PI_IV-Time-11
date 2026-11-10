"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useRecurso } from "@/lib/recurso";
import type { Aluno, Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { botaoPrincipal, botaoSecundario } from "./ui/estilos";

export function DetalhesTurma() {
  const { id } = useParams<{ id: string }>();
  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const alunos = useRecurso<Aluno[]>(`/api/turmas/${id}/alunos`);

  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
          <div>
            <Link href="/" className="text-sm underline">
              ← Turmas
            </Link>
            {turma.situacao === "pronto" && (
              <h1 className="mt-2 text-xl font-semibold">
                {turma.dados.disciplina} · {turma.dados.nome}
              </h1>
            )}
          </div>

          {turma.situacao === "carregando" && <Carregando texto="Carregando turma..." />}
          {turma.situacao === "erro" && (
            <ErroDeCarga mensagem={turma.erro.message} aoTentarNovamente={turma.recarregar} />
          )}

          {turma.situacao === "pronto" && (
            <>
              <div className="flex gap-2 text-sm">
                <Link href={`/turmas/${id}/chamada`} className={`${botaoPrincipal} px-3 py-2`}>
                  Abrir chamada
                </Link>
                <Link href={`/turmas/${id}/matricula`} className={`${botaoSecundario} px-3 py-2`}>
                  Matricular alunos
                </Link>
              </div>

              {alunos.situacao === "carregando" && <Carregando texto="Carregando alunos..." />}
              {alunos.situacao === "erro" && (
                <ErroDeCarga mensagem={alunos.erro.message} aoTentarNovamente={alunos.recarregar} />
              )}
              {alunos.situacao === "pronto" &&
                (alunos.dados.length === 0 ? (
                  <p className="text-sm opacity-70">Nenhum aluno matriculado nesta turma.</p>
                ) : (
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-borda-forte">
                        <th className="py-2 font-semibold">Aluno</th>
                        <th className="py-2 font-semibold">Matrícula</th>
                      </tr>
                    </thead>
                    <tbody>
                      {alunos.dados.map((aluno) => (
                        <tr key={aluno.id} className="border-b border-borda-suave">
                          <td className="py-2">{aluno.nome}</td>
                          <td className="py-2">{aluno.matricula}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ))}
            </>
          )}
        </main>
      )}
    </AreaAutenticada>
  );
}
