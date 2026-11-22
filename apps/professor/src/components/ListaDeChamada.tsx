"use client";

import Link from "next/link";
import { useState } from "react";
import { filtrarPorNome, type Marcacoes } from "@/lib/presencas";
import type { Recurso } from "@/lib/recurso";
import type { Aluno } from "@/lib/tipos";
import { Botao } from "./ui/Botao";
import { Campo } from "./ui/Campo";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";

interface ListaDeChamadaProps {
  turmaId: string;
  alunos: Recurso<Aluno[]>;
  marcacoes: Marcacoes;
  /** Sem conexão com o servidor, a lista fica só para leitura. */
  habilitada: boolean;
  emLote: boolean;
  alternar: (alunoId: string) => void;
  marcarLote: (presente: boolean) => void;
}

/** Lista da chamada clássica (P6): marcar todos, limpar, buscar por nome e marcar um a um. */
export function ListaDeChamada({ turmaId, alunos, marcacoes, habilitada, emLote, alternar, marcarLote }: ListaDeChamadaProps) {
  const [busca, setBusca] = useState("");

  if (alunos.situacao === "carregando") return <Carregando texto="Carregando alunos..." />;
  if (alunos.situacao === "erro") {
    return <ErroDeCarga mensagem={alunos.erro.message} aoTentarNovamente={alunos.recarregar} />;
  }
  if (alunos.dados.length === 0) {
    return (
      <p className="text-sm">
        Nenhum aluno matriculado nesta turma.{" "}
        <Link href={`/turmas/${turmaId}/matricula`} className="underline">
          Matricular alunos
        </Link>
      </p>
    );
  }

  const visiveis = filtrarPorNome(alunos.dados, busca);
  const bloqueada = !habilitada || emLote;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-2">
        <Botao onClick={() => marcarLote(true)} disabled={bloqueada}>
          Marcar todos presentes
        </Botao>
        <Botao variante="secundario" onClick={() => marcarLote(false)} disabled={bloqueada}>
          Limpar
        </Botao>
        <div className="min-w-48 flex-1">
          <Campo
            rotulo="Buscar aluno"
            type="search"
            name="busca"
            placeholder="Nome do aluno"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
        </div>
      </div>

      {visiveis.length === 0 ? (
        <p role="status" className="text-sm text-suave">
          Nenhum aluno encontrado para “{busca.trim()}”.
        </p>
      ) : (
        <ul aria-label="Alunos matriculados" className="border-t border-borda-suave">
          {visiveis.map((aluno) => {
            const presente = marcacoes[aluno.id] === true;
            return (
              <li key={aluno.id} className="border-b border-borda-suave">
                <label
                  className={`flex min-h-12 items-center gap-3 px-2 py-2 ${presente ? "bg-realce" : ""} ${
                    bloqueada ? "cursor-not-allowed" : "cursor-pointer"
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={presente}
                    disabled={bloqueada}
                    onChange={() => alternar(aluno.id)}
                    className="size-5 shrink-0 accent-primaria"
                  />
                  <span className="min-w-0 flex-1">{aluno.nome}</span>
                  <span className="font-mono text-sm text-suave">{aluno.matricula}</span>
                  <span className={`w-16 text-right text-sm font-semibold ${presente ? "text-sucesso" : "text-suave"}`}>
                    {presente ? "Presente" : "Ausente"}
                  </span>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
