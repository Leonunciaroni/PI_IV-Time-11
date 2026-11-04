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
      await enviar<ResultadoMatricula>(`/api/turmas/${id}/matriculas`, { alunoIds: [...selecionados] });
      router.push(`/turmas/${id}`);
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
            <Link href={`/turmas/${id}`} className="text-sm underline">
              ← {turma.situacao === "pronto" ? turma.dados.nome : "Turma"}
            </Link>
            <h1 className="mt-2 text-xl font-semibold">Matricular alunos</h1>
          </div>

          <form onSubmit={buscar} className="flex items-end gap-2">
            <div className="flex-1">
              <Campo
                rotulo="Buscar aluno por nome ou matrícula"
                name="busca"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
              />
            </div>
            <Botao type="submit">Buscar</Botao>
          </form>

          {candidatos.situacao === "carregando" && <Carregando texto="Carregando alunos..." />}
          {candidatos.situacao === "erro" && (
            <ErroDeCarga mensagem={candidatos.erro.message} aoTentarNovamente={candidatos.recarregar} />
          )}
          {candidatos.situacao === "pronto" &&
            (candidatos.dados.length === 0 ? (
              <p className="text-sm opacity-70">Nenhum aluno encontrado para matricular.</p>
            ) : (
              <ul className="flex flex-col gap-1">
                {candidatos.dados.map((aluno) => (
                  <li key={aluno.id}>
                    <label className="flex cursor-pointer items-center gap-3 rounded-controle px-2 py-1 hover:bg-realce">
                      <input
                        type="checkbox"
                        checked={selecionados.has(aluno.id)}
                        onChange={() => alternar(aluno.id)}
                      />
                      <span>{aluno.nome}</span>
                      <span className="text-sm opacity-70">{aluno.matricula}</span>
                    </label>
                  </li>
                ))}
              </ul>
            ))}

          <div className="flex flex-col items-end gap-2">
            <Botao onClick={matricular} disabled={selecionados.size === 0 || enviando}>
              {enviando ? "Matriculando..." : "Matricular selecionados"}
            </Botao>
            <MensagemDeErro>{erro}</MensagemDeErro>
          </div>
        </main>
      )}
    </AreaAutenticada>
  );
}
