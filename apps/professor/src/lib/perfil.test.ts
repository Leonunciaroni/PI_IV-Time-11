import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { editarPerfil, ErroPerfil, validarSenha } from "./perfil";
import { atualizarUsuario, encerrarSessao, lerSessao, salvarSessao } from "./sessao";
import type { TokenResponse, Usuario } from "./tipos";

const usuario: Usuario = { id: "usuario", nome: "Nome", email: "nome@teste.br", papel: "PROFESSOR", matricula: "P1" };
const resposta: TokenResponse = { token: "token-original", tipo: "Bearer", expiraEm: "2099-01-01T00:00:00Z", usuario };

beforeEach(() => {
  const dados = new Map<string, string>();
  vi.stubGlobal("localStorage", { getItem: (k: string) => dados.get(k) ?? null, setItem: (k: string, v: string) => dados.set(k, v), removeItem: (k: string) => dados.delete(k) });
  salvarSessao(resposta);
});
afterEach(() => vi.unstubAllGlobals());

describe("perfil e sessão", () => {
  it("preserva JWT e validade ao refletir os dados devolvidos pelo PATCH", () => {
    const novo = { ...usuario, nome: "Novo nome", email: "novo@teste.br" };
    expect(atualizarUsuario(novo, resposta.token)).toBe(true);
    expect(lerSessao()).toEqual({ token: resposta.token, expiraEm: Date.parse(resposta.expiraEm), usuario: novo });
  });
  it("uma resposta atrasada não recria sessão depois do logout", () => {
    encerrarSessao();
    expect(atualizarUsuario(usuario, resposta.token)).toBe(false);
    expect(lerSessao()).toBeNull();
  });
  it("uma resposta da sessão anterior não sobrescreve outro login", () => {
    salvarSessao({ ...resposta, token: "outro-token", usuario: { ...usuario, id: "outra-pessoa" } });
    expect(atualizarUsuario(usuario, resposta.token)).toBe(false);
    expect(lerSessao()?.usuario.id).toBe("outra-pessoa");
  });
  it("envia PATCH autenticado e usa a resposta Usuario sem exigir um novo JWT", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(usuario), { status: 200 }));
    vi.stubGlobal("fetch", fetch);
    const signal = new AbortController().signal;
    expect(await editarPerfil(resposta.token, { nome: "Nome", email: "nome@teste.br" }, signal)).toEqual(usuario);
    expect(fetch).toHaveBeenCalledWith("/api/auth/me", expect.objectContaining({ method: "PATCH", signal, headers: { Authorization: "Bearer token-original", "Content-Type": "application/json" }, body: JSON.stringify({ nome: "Nome", email: "nome@teste.br" }) }));
  });
  it("exibe o conflito de e-mail e a validação enviados pela API", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ detail: "E-mail já em uso.", campos: { email: "Informe outro e-mail." } }), { status: 409 })));
    await expect(editarPerfil(resposta.token, { nome: "Nome", email: "ocupado@teste.br" }, new AbortController().signal)).rejects.toMatchObject({ message: "E-mail já em uso.", campos: { email: "Informe outro e-mail." } });
  });
  it("um 401 da requisição antiga não encerra um login mais recente", async () => {
    salvarSessao({ ...resposta, token: "novo-token" });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 401 })));
    await expect(editarPerfil(resposta.token, { nome: "Nome", email: "nome@teste.br" }, new AbortController().signal)).rejects.toBeInstanceOf(ErroPerfil);
    expect(lerSessao()?.token).toBe("novo-token");
  });
  it("encerra a sessão cujo token foi recusado", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 401 })));
    await expect(editarPerfil(resposta.token, { senhaAtual: "atual", novaSenha: "senha-nova" }, new AbortController().signal)).rejects.toBeInstanceOf(ErroPerfil);
    expect(lerSessao()).toBeNull();
  });
});

describe("troca de senha", () => {
  it("aceita a confirmação correta, preservando os espaços da senha", () => {
    expect(() => validarSenha(" senha atual ", " nova senha ", " nova senha ")).not.toThrow();
  });
  it("recusa confirmação diferente e senha atual ausente", () => {
    expect(() => validarSenha("", "nova-senha", "diferente")).toThrow(ErroPerfil);
  });
  it("impede senha Unicode acima do limite de bytes do BCrypt", () => {
    expect(() => validarSenha("senha-atual", "é".repeat(37), "é".repeat(37))).toThrow(ErroPerfil);
    expect(() => validarSenha("senha-atual", "é".repeat(36), "é".repeat(36))).not.toThrow();
  });
});
