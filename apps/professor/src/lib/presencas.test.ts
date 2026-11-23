/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { describe, expect, it } from "vitest";
import {
  contarPresentes,
  filtrarPorNome,
  juntarPresencasAoVivo,
  listarPresentes,
  marcacoesExibidas,
  marcarTodos,
  presencasAoVivoVazias,
  soltarPendentes,
  totaisAoVivo,
  type EntradaAoVivo,
  type PresencasAoVivo,
} from "./presencas";
import type { PresencaAtualizada, PresenteNaSessao, SessaoEstado } from "./protocolo";

const alunos = [
  { id: "a1", nome: "Ana Souza", matricula: "24001234" },
  { id: "a2", nome: "Bruno Lima", matricula: "24001777" },
  { id: "a3", nome: "João Ribeiro", matricula: "24002084" },
  { id: "a4", nome: "Larissa Mendes", matricula: "24002096" },
];

describe("filtrarPorNome", () => {
  it("devolve todos com a busca vazia ou só com espaços", () => {
    expect(filtrarPorNome(alunos, "")).toEqual(alunos);
    expect(filtrarPorNome(alunos, "   ")).toEqual(alunos);
  });

  it("encontra parte do nome sem diferenciar maiúsculas nem acentos", () => {
    expect(filtrarPorNome(alunos, "joao").map((a) => a.id)).toEqual(["a3"]);
    expect(filtrarPorNome(alunos, "SOUZA").map((a) => a.id)).toEqual(["a1"]);
    expect(filtrarPorNome(alunos, "  li ").map((a) => a.id)).toEqual(["a2"]);
  });

  it("devolve lista vazia quando ninguém confere", () => {
    expect(filtrarPorNome(alunos, "xyz")).toEqual([]);
  });
});

describe("marcarTodos", () => {
  it("marca todos os alunos com o mesmo valor", () => {
    expect(marcarTodos(alunos, true)).toEqual({ a1: true, a2: true, a3: true, a4: true });
    expect(marcarTodos(alunos, false)).toEqual({ a1: false, a2: false, a3: false, a4: false });
  });
});

describe("contarPresentes", () => {
  it("conta só quem está marcado como presente, entre os alunos da lista", () => {
    expect(contarPresentes(alunos, { a1: true, a2: false, a4: true })).toBe(2);
    expect(contarPresentes(alunos, {})).toBe(0);
    // Marcação de alguém que saiu da turma não entra na conta
    expect(contarPresentes(alunos, { a1: true, x9: true })).toBe(1);
  });
});

function estado(presentes: PresenteNaSessao[], totalMatriculados = 4): SessaoEstado {
  return { sessaoId: "s1", modo: "QRCODE", abertaEm: 1_000, presentes, totalMatriculados };
}

function presente(alunoId: string, nome: string, registradoEm: number): PresenteNaSessao {
  return { alunoId, nome, origem: "QRCODE", registradoEm };
}

function evento(
  alunoId: string,
  nomeAluno: string,
  registradoEm: number,
  { presente = true, totalPresentes = 1, origem = "QRCODE" as PresencaAtualizada["origem"] } = {},
): PresencaAtualizada {
  return { sessaoId: "s1", alunoId, nomeAluno, presente, origem, registradoEm, totalPresentes, totalMatriculados: 4 };
}

function aplicar(...entradas: EntradaAoVivo[]): PresencasAoVivo {
  return entradas.reduce(juntarPresencasAoVivo, presencasAoVivoVazias);
}

const pedido: EntradaAoVivo = { tipo: "pedido" };
const comEstado = (e: SessaoEstado): EntradaAoVivo => ({ tipo: "estado", estado: e });
const comEvento = (e: PresencaAtualizada): EntradaAoVivo => ({ tipo: "evento", evento: e });

describe("juntarPresencasAoVivo", () => {
  it("começa vazia e sem estado carregado", () => {
    expect(listarPresentes(presencasAoVivoVazias)).toEqual([]);
    expect(presencasAoVivoVazias.carregado).toBe(false);
    expect(totaisAoVivo(presencasAoVivoVazias)).toEqual({ presentes: 0, matriculados: null });
  });

  it("estado antes do evento: soma o evento e ordena do mais recente para o mais antigo", () => {
    const juntas = aplicar(
      pedido,
      comEstado(estado([presente("a1", "Ana Souza", 100)])),
      comEvento(evento("a2", "Bruno Lima", 200, { totalPresentes: 2, origem: "SOM" })),
    );
    expect(juntas.carregado).toBe(true);
    expect(listarPresentes(juntas)).toEqual([
      { alunoId: "a2", nome: "Bruno Lima", origem: "SOM", registradoEm: 200 },
      { alunoId: "a1", nome: "Ana Souza", origem: "QRCODE", registradoEm: 100 },
    ]);
    expect(totaisAoVivo(juntas)).toEqual({ presentes: 2, matriculados: 4 });
  });

  it("evento antes do estado: aparece na hora e continua depois que o estado chega", () => {
    const soEvento = aplicar(pedido, comEvento(evento("a2", "Bruno Lima", 200)));
    expect(soEvento.carregado).toBe(false);
    expect(listarPresentes(soEvento).map((p) => p.alunoId)).toEqual(["a2"]);

    // O estado foi lido antes da gravação do a2, então não o traz; o evento não pode sumir
    const juntas = juntarPresencasAoVivo(soEvento, comEstado(estado([presente("a1", "Ana Souza", 100)])));
    expect(listarPresentes(juntas).map((p) => p.alunoId)).toEqual(["a2", "a1"]);
    expect(totaisAoVivo(juntas)).toEqual({ presentes: 2, matriculados: 4 });
  });

  it("a mesma presença no estado e no evento aparece uma vez só, em qualquer ordem", () => {
    const antes = aplicar(pedido, comEvento(evento("a1", "Ana Souza", 100)), comEstado(estado([presente("a1", "Ana Souza", 100)])));
    const depois = aplicar(pedido, comEstado(estado([presente("a1", "Ana Souza", 100)])), comEvento(evento("a1", "Ana Souza", 100)));
    expect(listarPresentes(antes)).toHaveLength(1);
    expect(listarPresentes(depois)).toHaveLength(1);
  });

  it("evento repetido não duplica", () => {
    const juntas = aplicar(pedido, comEstado(estado([])), comEvento(evento("a1", "Ana", 100)), comEvento(evento("a1", "Ana", 100)));
    expect(listarPresentes(juntas)).toHaveLength(1);
  });

  it("presente: false tira o aluno da lista, mesmo vindo antes do estado que ainda o traz", () => {
    const depois = aplicar(
      pedido,
      comEstado(estado([presente("a1", "Ana", 100), presente("a2", "Bruno", 110)])),
      comEvento(evento("a1", "Ana", 300, { presente: false, totalPresentes: 1 })),
    );
    expect(listarPresentes(depois).map((p) => p.alunoId)).toEqual(["a2"]);
    expect(totaisAoVivo(depois)).toEqual({ presentes: 1, matriculados: 4 });

    // Desmarcado depois da leitura do estado: o evento é mais novo que o item do estado
    const antes = aplicar(
      pedido,
      comEvento(evento("a1", "Ana", 300, { presente: false })),
      comEstado(estado([presente("a1", "Ana", 100)])),
    );
    expect(listarPresentes(antes)).toEqual([]);
  });

  it("um evento mais antigo não desfaz um registro mais novo", () => {
    const juntas = aplicar(
      pedido,
      comEstado(estado([])),
      comEvento(evento("a1", "Ana", 300, { presente: false })),
      comEvento(evento("a1", "Ana", 200)),
    );
    expect(listarPresentes(juntas)).toEqual([]);
  });

  it("reconexão: o estado novo substitui o anterior, inclusive o que mudou com a conexão fora", () => {
    const antes = aplicar(
      pedido,
      comEstado(estado([presente("a1", "Ana", 100)])),
      comEvento(evento("a2", "Bruno", 200, { totalPresentes: 2 })),
    );
    // Com a conexão fora, a1 foi desmarcado e a3 registrou presença; os eventos não voltam
    const durante = juntarPresencasAoVivo(antes, pedido);
    expect(listarPresentes(durante).map((p) => p.alunoId)).toEqual(["a2", "a1"]);
    const depois = juntarPresencasAoVivo(
      durante,
      comEstado(estado([presente("a2", "Bruno", 200), presente("a3", "João", 400)], 5)),
    );
    expect(listarPresentes(depois).map((p) => p.alunoId)).toEqual(["a3", "a2"]);
    expect(totaisAoVivo(depois)).toEqual({ presentes: 2, matriculados: 5 });
  });

  it("usa os totais do último evento depois do estado", () => {
    const juntas = aplicar(pedido, comEstado(estado([])), comEvento(evento("a1", "Ana", 100, { totalPresentes: 7 })));
    expect(totaisAoVivo(juntas)).toEqual({ presentes: 7, matriculados: 4 });
  });
});

describe("marcações da clássica a partir do servidor", () => {
  it("o estado do servidor define quem está presente", () => {
    const juntas = aplicar(pedido, comEstado(estado([presente("a1", "Ana Souza", 100), presente("a3", "João Ribeiro", 110)])));
    const marcacoes = marcacoesExibidas(listarPresentes(juntas), {});
    expect(marcacoes).toEqual({ a1: true, a3: true });
    expect(contarPresentes(alunos, marcacoes)).toBe(2);
  });

  it("evento de outra aba ou navegador atualiza a lista", () => {
    const juntas = aplicar(
      pedido,
      comEstado(estado([presente("a1", "Ana Souza", 100)])),
      comEvento(evento("a1", "Ana Souza", 200, { presente: false, origem: "MANUAL" })),
      comEvento(evento("a2", "Bruno Lima", 210, { origem: "MANUAL" })),
    );
    const marcacoes = marcacoesExibidas(listarPresentes(juntas), {});
    expect(marcacoes).toEqual({ a2: true });
    expect(contarPresentes(alunos, marcacoes)).toBe(1);
  });

  it("o toque sem resposta fica por cima do servidor até ser solto", () => {
    const presentes = [{ alunoId: "a1" }];
    const pendentes = { a1: { presente: false, id: 1 }, a2: { presente: true, id: 1 } };
    expect(marcacoesExibidas(presentes, pendentes)).toEqual({ a1: false, a2: true });
    expect(marcacoesExibidas(presentes, soltarPendentes(pendentes, 1))).toEqual({ a1: true });
  });

  it("soltar um toque antigo não desfaz um toque mais novo no mesmo aluno", () => {
    // Toque 1 marcou a1, toque 2 desmarcou; a resposta do toque 1 chega primeiro
    const pendentes = { a1: { presente: false, id: 2 } };
    expect(soltarPendentes(pendentes, 1)).toEqual(pendentes);
    expect(soltarPendentes(pendentes, 2)).toEqual({});
  });
});
