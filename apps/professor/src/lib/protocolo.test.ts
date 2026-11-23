/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { describe, expect, it } from "vitest";
import { mensagemDoErro } from "./protocolo";

describe("mensagemDoErro", () => {
  it("troca a mensagem técnica do servidor por uma frase para o professor", () => {
    expect(mensagemDoErro({ codigo: "SESSAO_JA_ABERTA", mensagem: "Já existe uma sessão aberta para a turma" })).toBe(
      "Já existe uma chamada aberta para esta turma.",
    );
    expect(mensagemDoErro({ codigo: "MENSAGEM_INVALIDA", mensagem: "Informe turmaId e modo" })).toBe(
      "O servidor não entendeu o pedido. Recarregue a página e tente de novo.",
    );
    expect(mensagemDoErro({ codigo: "PERMISSAO_NEGADA", mensagem: "Turma não encontrada" })).toBe(
      "Você não tem permissão para esta chamada. Confira se a turma é sua.",
    );
  });

  it("diz quando a chamada foi encerrada por tempo limite", () => {
    expect(mensagemDoErro({ codigo: "SESSAO_ENCERRADA", mensagem: "A sessão foi encerrada por tempo limite" })).toBe(
      "Esta chamada foi encerrada automaticamente por tempo limite.",
    );
    expect(mensagemDoErro({ codigo: "SESSAO_ENCERRADA", mensagem: "A sessão já foi encerrada" })).toBe(
      "Esta chamada já foi encerrada.",
    );
  });

  it("usa a mensagem do servidor para um código que o app ainda não conhece", () => {
    expect(mensagemDoErro({ codigo: "CODIGO_NOVO", mensagem: "Algo novo aconteceu" })).toBe("Algo novo aconteceu");
    expect(mensagemDoErro({ codigo: "CODIGO_NOVO", mensagem: "" })).toBe(
      "O servidor de chamada teve um erro. Tente de novo.",
    );
  });
});
