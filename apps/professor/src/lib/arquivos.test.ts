import { describe, expect, it } from "vitest";
import { nomeDoArquivo, resumoImportacao } from "./arquivos";

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
