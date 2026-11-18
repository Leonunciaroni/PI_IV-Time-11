// Envio e download de arquivos pela API (importação e exportação de CSV, #70). Fica fora do api.ts
// porque o JSON de lá não serve para multipart nem para baixar um arquivo; as regras de erro e de
// sessão são as mesmas.

import { ErroApi } from "./api";
import { encerrarSessao, lerSessao } from "./sessao";
import type { Problema, ResultadoImportacao } from "./tipos";

function cabecalhoDeAutorizacao(): Record<string, string> {
  const sessao = lerSessao();
  if (!sessao) throw new ErroApi("Sua sessão expirou. Entre novamente.", 401);
  return { Authorization: `Bearer ${sessao.token}` };
}

async function buscar(caminho: string, opcoes: RequestInit): Promise<Response> {
  let resposta: Response;
  try {
    resposta = await fetch(caminho, opcoes);
  } catch {
    throw new ErroApi("Não foi possível conectar ao servidor. Verifique sua conexão.", 0);
  }
  if (!resposta.ok) {
    // Token recusado: o AreaAutenticada leva ao login quando a sessão some
    if (resposta.status === 401) encerrarSessao();
    const problema = (await resposta.json().catch(() => null)) as Problema | null;
    throw new ErroApi(problema?.detail ?? "Erro inesperado. Tente novamente.", resposta.status);
  }
  return resposta;
}

/** POST multipart com um arquivo no campo indicado; a resposta é JSON. */
export async function enviarArquivo<T>(caminho: string, campo: string, arquivo: File): Promise<T> {
  const corpo = new FormData();
  corpo.append(campo, arquivo);
  // Sem Content-Type: o navegador monta o multipart com o boundary certo
  const resposta = await buscar(caminho, { method: "POST", headers: cabecalhoDeAutorizacao(), body: corpo });
  return (await resposta.json()) as T;
}

/**
 * Nome do arquivo informado pelo servidor em Content-Disposition. Prefere `filename*` (RFC 5987,
 * que leva acentos) a `filename`; sem nenhum dos dois, usa o padrão. Tira barras para o nome não
 * virar caminho.
 */
export function nomeDoArquivo(cabecalho: string | null, padrao: string): string {
  if (!cabecalho) return padrao;
  let nome: string | null = null;
  const estendido = /filename\*\s*=\s*([^']*)'[^']*'([^;]+)/i.exec(cabecalho);
  if (estendido) {
    try {
      nome = decodeURIComponent(estendido[2].trim().replace(/^"|"$/g, ""));
    } catch {
      nome = null;
    }
  }
  if (!nome) {
    const simples = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(cabecalho);
    if (simples) nome = (simples[2] ?? simples[1]).trim();
  }
  const limpo = nome?.replace(/[\\/]/g, "-").trim();
  return limpo ? limpo : padrao;
}

/** Baixa um arquivo autenticado e entrega ao navegador com o nome sugerido pelo servidor. */
export async function baixarArquivo(caminho: string, nomePadrao: string): Promise<string> {
  const resposta = await buscar(caminho, { headers: cabecalhoDeAutorizacao() });
  const nome = nomeDoArquivo(resposta.headers.get("Content-Disposition"), nomePadrao);
  const endereco = URL.createObjectURL(await resposta.blob());
  const link = document.createElement("a");
  link.href = endereco;
  link.download = nome;
  document.body.append(link);
  link.click();
  link.remove();
  // O download já começou; o endereço pode ser liberado logo depois
  setTimeout(() => URL.revokeObjectURL(endereco), 1000);
  return nome;
}

/** Só a extensão: o tipo informado pelo navegador para CSV varia (text/csv, application/vnd.ms-excel...). */
export function eArquivoCsv(nome: string): boolean {
  return /\.csv$/i.test(nome.trim());
}

export const MENSAGEM_NAO_CSV = "Escolha um arquivo .csv. No Excel, use Salvar como → CSV.";

/**
 * Mensagem para uma falha na importação. A API responde 500 quando não consegue ler o arquivo
 * (aspas fora do lugar, arquivo binário); em vez do "erro inesperado", explica o que conferir.
 */
export function mensagemDeErroDaImportacao(erro: unknown): string {
  if (erro instanceof ErroApi && erro.status >= 500) {
    return "Não foi possível ler o arquivo. Confira se ele é um CSV com as colunas nome, email e matricula.";
  }
  return erro instanceof ErroApi ? erro.message : "Erro inesperado. Tente novamente.";
}

const plural = (n: number, um: string, varios: string) => (n === 1 ? `1 ${um}` : `${n} ${varios}`);

/** Resumo do relatório de importação, para o aviso no topo da lista. */
export function resumoImportacao({ importados, jaMatriculados, rejeitados }: ResultadoImportacao): string {
  const partes = [plural(importados, "aluno importado", "alunos importados")];
  if (jaMatriculados > 0) partes.push(plural(jaMatriculados, "já estava na turma", "já estavam na turma"));
  if (rejeitados.length > 0) partes.push(plural(rejeitados.length, "linha recusada", "linhas recusadas"));
  return `${partes.join("; ")}.`;
}
