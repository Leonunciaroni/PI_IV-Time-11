/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { describe, expect, it } from "vitest";
import { ErroApi } from "./api";
import { eArquivoCsv, mensagemDeErroDaImportacao, nomeDoArquivo, resumoImportacao } from "./arquivos";

describe("eArquivoCsv", () => {
  it("aceita .csv em qualquer caixa", () => {
    expect(eArquivoCsv("alunos.csv")).toBe(true);
    expect(eArquivoCsv("TURMA A.CSV")).toBe(true);
  });

  it("recusa planilha, imagem, texto e nome sem extensão", () => {
    for (const nome of ["alunos.xlsx", "foto.png", "lista.txt", "alunos", "alunos.csv.png"]) {
      expect(eArquivoCsv(nome)).toBe(false);
    }
  });
});

describe("mensagemDeErroDaImportacao", () => {
  it("explica o que conferir quando a API não consegue ler o arquivo (500)", () => {
    expect(mensagemDeErroDaImportacao(new ErroApi("Erro inesperado. Tente novamente.", 500))).toMatch(
      /Não foi possível ler o arquivo/,
    );
  });

  it("mantém a mensagem da API nos outros erros", () => {
    const erro = new ErroApi("O cabeçalho precisa ter as colunas nome, email e matricula.", 400);
    expect(mensagemDeErroDaImportacao(erro)).toBe(erro.message);
    expect(mensagemDeErroDaImportacao(new Error("x"))).toBe("Erro inesperado. Tente novamente.");
  });
});

describe("nomeDoArquivo", () => {
  it("prefere filename* com acentos (RFC 5987)", () => {
    expect(
      nomeDoArquivo(
        `attachment; filename="frequencia-Turma A.csv"; filename*=UTF-8''frequencia-Engenharia%20de%20Software%20%C2%B7%20Turma%20A.csv`,
        "padrao.csv",
      ),
    ).toBe("frequencia-Engenharia de Software · Turma A.csv");
  });

  it("aceita filename entre aspas ou sem aspas", () => {
    expect(nomeDoArquivo(`attachment; filename="chamada-turma-a-2026-11-17.csv"`, "x.csv")).toBe(
      "chamada-turma-a-2026-11-17.csv",
    );
    expect(nomeDoArquivo("attachment; filename=frequencia.csv", "x.csv")).toBe("frequencia.csv");
  });

  it("usa o padrão sem cabeçalho ou sem nome", () => {
    expect(nomeDoArquivo(null, "frequencia.csv")).toBe("frequencia.csv");
    expect(nomeDoArquivo("attachment", "frequencia.csv")).toBe("frequencia.csv");
    expect(nomeDoArquivo(`attachment; filename=""`, "frequencia.csv")).toBe("frequencia.csv");
  });

  it("troca barras para o nome não virar caminho", () => {
    expect(nomeDoArquivo(`attachment; filename="../turma/a.csv"`, "x.csv")).toBe("..-turma-a.csv");
  });

  it("cai no filename simples se o filename* estiver mal codificado", () => {
    expect(nomeDoArquivo(`attachment; filename="ok.csv"; filename*=UTF-8''%E0%A4%A`, "x.csv")).toBe("ok.csv");
  });
});

describe("resumoImportacao", () => {
  it("resume importados, repetidos e recusados", () => {
    expect(
      resumoImportacao({
        importados: 3,
        jaMatriculados: 1,
        rejeitados: [
          { linha: 4, motivo: "Aluno não cadastrado." },
          { linha: 6, motivo: "E-mail repetido no arquivo." },
        ],
        totalAlunos: 14,
      }),
    ).toBe("3 alunos importados; 1 já estava na turma; 2 linhas recusadas.");
  });

  it("usa o singular e omite o que for zero", () => {
    expect(resumoImportacao({ importados: 1, jaMatriculados: 0, rejeitados: [], totalAlunos: 1 })).toBe(
      "1 aluno importado.",
    );
    expect(resumoImportacao({ importados: 0, jaMatriculados: 2, rejeitados: [], totalAlunos: 2 })).toBe(
      "0 alunos importados; 2 já estavam na turma.",
    );
  });
});
