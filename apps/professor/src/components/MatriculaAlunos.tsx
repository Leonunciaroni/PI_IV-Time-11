"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useRef, useState, type ChangeEvent, type SubmitEvent } from "react";
import { ErroApi, enviar } from "@/lib/api";
import {
  MENSAGEM_NAO_CSV,
  eArquivoCsv,
  enviarArquivo,
  mensagemDeErroDaImportacao,
  resumoImportacao,
} from "@/lib/arquivos";
import { useRecurso } from "@/lib/recurso";
import type { Aluno, ResultadoImportacao, ResultadoMatricula, Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { AvisoDeSucesso } from "./ui/AvisoDeSucesso";
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
  const seletorDeArquivo = useRef<HTMLInputElement>(null);
  const [importando, setImportando] = useState(false);
  const [importacao, setImportacao] = useState<ResultadoImportacao | null>(null);
  const [erroImportacao, setErroImportacao] = useState<string | null>(null);

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

  /** CSV com nome, email e matricula: a API matricula quem já tem conta e devolve o relatório. */
  async function importar(evento: ChangeEvent<HTMLInputElement>) {
    const arquivo = evento.target.files?.[0];
    // Limpa a escolha para o mesmo arquivo poder ser enviado de novo depois de corrigido
    evento.target.value = "";
    if (!arquivo) return;
    setImportacao(null);
    // O seletor já filtra .csv, mas o professor pode trocar para "Todos os arquivos"
    if (!eArquivoCsv(arquivo.name)) {
      setErroImportacao(MENSAGEM_NAO_CSV);
      return;
    }
    setErroImportacao(null);
    setImportando(true);
    try {
      const resultado = await enviarArquivo<ResultadoImportacao>(`/api/turmas/${id}/importacao`, "arquivo", arquivo);
      setImportacao(resultado);
      // Quem entrou na turma sai da lista de candidatos (se ela já estiver carregando, virá atualizada)
      if (candidatos.situacao !== "carregando") candidatos.recarregar();
    } catch (e) {
      setErroImportacao(mensagemDeErroDaImportacao(e));
    } finally {
      setImportando(false);
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

          <section aria-labelledby="titulo-importar" className="flex flex-col gap-3 border-t border-borda-suave pt-6">
            <div>
              <h2 id="titulo-importar" className="font-semibold">
                Importar lista CSV
              </h2>
              <p className="text-sm opacity-70">
                O arquivo precisa de um cabeçalho com <code>nome</code>, <code>email</code> e <code>matricula</code>.
                Só entram alunos que já têm conta no sistema; os demais aparecem no relatório.
              </p>
            </div>
            <input
              ref={seletorDeArquivo}
              type="file"
              accept=".csv,text/csv"
              onChange={importar}
              hidden
              aria-label="Arquivo CSV com a lista de alunos"
            />
            <div>
              <Botao variante="secundario" onClick={() => seletorDeArquivo.current?.click()} disabled={importando}>
                {importando ? "Importando..." : "Escolher arquivo CSV"}
              </Botao>
            </div>

            {importacao && (
              <div className="flex flex-col gap-3">
                <AvisoDeSucesso>
                  {resumoImportacao(importacao)} A turma tem agora{" "}
                  {importacao.totalAlunos === 1 ? "1 aluno" : `${importacao.totalAlunos} alunos`}.
                </AvisoDeSucesso>
                {importacao.rejeitados.length > 0 && (
                  <div className="flex flex-col gap-1">
                    <h3 className="text-sm font-semibold">Linhas recusadas</h3>
                    <ul className="flex flex-col gap-1 text-sm">
                      {importacao.rejeitados.map((rejeitado) => (
                        <li key={rejeitado.linha} className="flex gap-3">
                          <span className="w-16 shrink-0 tabular-nums opacity-70">Linha {rejeitado.linha}</span>
                          <span className="min-w-0 break-words">{rejeitado.motivo}</span>
                        </li>
                      ))}
                    </ul>
                    <p className="text-xs opacity-60">A linha 1 é o cabeçalho.</p>
                  </div>
                )}
                <Link href={`/turmas/${id}`} className="self-start text-sm underline">
                  Ver os alunos da turma
                </Link>
              </div>
            )}
            <MensagemDeErro>{erroImportacao}</MensagemDeErro>
          </section>
        </main>
      )}
    </AreaAutenticada>
  );
}
