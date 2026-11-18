"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type SubmitEvent } from "react";
import { ErroApi, enviar } from "@/lib/api";
import { useRecurso } from "@/lib/recurso";
import type { Aluno, ResultadoMatricula, Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { Botao } from "./ui/Botao";
import { Campo } from "./ui/Campo";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { MensagemDeErro } from "./ui/MensagemDeErro";

export function MatriculaAlunos() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [busca, setBusca] = useState("");
  const [buscaAplicada, setBuscaAplicada] = useState("");
  const [selecionados, setSelecionados] = useState<ReadonlySet<string>>(new Set());
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const turma = useRecurso<Turma>(`/api/turmas/${id}`);
  const filtro = buscaAplicada ? `?busca=${encodeURIComponent(buscaAplicada)}` : "";
  const candidatos = useRecurso<Aluno[]>(`/api/turmas/${id}/candidatos${filtro}`);

  function buscar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    setBuscaAplicada(busca.trim());
  }

  function alternar(alunoId: string) {
    setSelecionados((atuais) => {
      const novos = new Set(atuais);
      if (!novos.delete(alunoId)) novos.add(alunoId);
      return novos;
    });
  }

  async function matricular() {
    setErro(null);
    setEnviando(true);
    try {
      const resultado = await enviar<ResultadoMatricula>(`/api/turmas/${id}/matriculas`, {
        alunoIds: [...selecionados],
      });
      // Os detalhes da turma confirmam quantos entraram
      router.push(`/turmas/${id}?matriculados=${resultado.matriculados}&ja=${resultado.jaMatriculados}`);
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : "Erro inesperado. Tente novamente.");
      setEnviando(false);
    }
  }

  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6">
          <div>
            <Link href={`/turmas/${id}`} className="text-sm underline break-words">
              ← {turma.situacao === "pronto" ? `${turma.dados.disciplina} · ${turma.dados.nome}` : "Turma"}
            </Link>
            <h1 className="mt-2 text-xl font-semibold">Matricular alunos</h1>
            <p className="text-sm opacity-70">A lista mostra só os alunos que ainda não estão na turma.</p>
          </div>

          <form onSubmit={buscar} role="search" className="flex items-end gap-2">
            {/* min-w-0: sem isto o campo não encolhe e empurra o botão para fora da tela no celular */}
            <div className="min-w-0 flex-1">
              <Campo
                rotulo="Buscar aluno por nome ou matrícula"
                name="busca"
                type="search"
                autoComplete="off"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
              />
            </div>
            <Botao type="submit" variante="secundario">
              Buscar
            </Botao>
          </form>

          {candidatos.situacao === "carregando" && <Carregando texto="Carregando alunos..." />}
          {candidatos.situacao === "erro" && (
            <ErroDeCarga mensagem={candidatos.erro.message} aoTentarNovamente={candidatos.recarregar} />
          )}
          {candidatos.situacao === "pronto" &&
            (candidatos.dados.length === 0 ? (
              <p className="text-sm opacity-70">
                {buscaAplicada
                  ? `Nenhum aluno encontrado para “${buscaAplicada}”. Quem já está na turma não aparece aqui.`
                  : "Todos os alunos cadastrados já estão nesta turma."}
              </p>
            ) : (
              <ul aria-label="Alunos que podem ser matriculados" className="flex flex-col gap-1">
                {candidatos.dados.map((aluno) => (
                  <li key={aluno.id}>
                    <label className="flex cursor-pointer items-center gap-3 rounded-controle px-2 py-1 hover:bg-realce">
                      <input
                        type="checkbox"
                        checked={selecionados.has(aluno.id)}
                        onChange={() => alternar(aluno.id)}
                        className="shrink-0 accent-primaria"
                      />
                      {/* Nome longo quebra a linha sem jogar a matrícula para baixo da caixa de seleção */}
                      <span className="flex min-w-0 flex-wrap items-baseline gap-x-3">
                        <span className="break-words">{aluno.nome}</span>
                        <span className="text-sm tabular-nums opacity-70">{aluno.matricula}</span>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            ))}

          <div className="flex flex-wrap items-center justify-end gap-x-4 gap-y-2">
            {/* A seleção vale entre buscas; o total avisa quem já marcou alunos que saíram da lista */}
            <p aria-live="polite" className="text-sm opacity-70">
              {selecionados.size === 0
                ? "Nenhum aluno selecionado"
                : selecionados.size === 1
                  ? "1 aluno selecionado"
                  : `${selecionados.size} alunos selecionados`}
            </p>
            <Botao onClick={matricular} disabled={selecionados.size === 0 || enviando}>
              {enviando ? "Matriculando..." : "Matricular selecionados"}
            </Botao>
          </div>
          <div className="-mt-4 flex justify-end">
            <MensagemDeErro>{erro}</MensagemDeErro>
          </div>
        </main>
      )}
    </AreaAutenticada>
  );
}
