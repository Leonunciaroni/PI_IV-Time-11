"use client";

import { use, useEffect, useState, useSyncExternalStore, type FormEvent } from "react";
import { browser } from "react-dom";
import { useRouter, useSearchParams } from "next/navigation";
import { ErroApi, login } from "@/lib/api";
import { assinarSessao, lerSessao, salvarSessao } from "@/lib/sessao";

export function FormularioLogin() {
  use(browser("A sessão do usuário fica no localStorage."));
  const router = useRouter();
  const parametros = useSearchParams();
  const jaAutenticado = useSyncExternalStore(assinarSessao, () => lerSessao() !== null, () => false);
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [contaDeOutraArea, setContaDeOutraArea] = useState(false);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (jaAutenticado) router.replace("/");
  }, [jaAutenticado, router]);

  if (jaAutenticado) return null;

  async function entrar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setContaDeOutraArea(false);
    setEnviando(true);
    try {
      const resposta = await login(email.trim(), senha);
      if (resposta.usuario.papel !== "PROFESSOR") {
        setContaDeOutraArea(true);
        setErro("Esta conta é de aluno.");
        return;
      }
      // Não deixa a senha no estado preservado da rota (<Activity>)
      setSenha("");
      // A mudança de sessão faz o efeito acima redirecionar para a página inicial
      salvarSessao(resposta);
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : "Erro inesperado. Tente novamente.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={entrar} className="flex flex-col gap-4">
      {parametros.get("cadastro") === "sucesso" && (
        <p role="status" className="rounded-md border border-green-600/30 bg-green-50 p-3 text-sm text-green-800 dark:bg-green-950 dark:text-green-200">
          Cadastro concluído! Entre com seu e-mail e senha.
        </p>
      )}
      <label className="flex flex-col gap-1 text-sm font-medium">
        E-mail
        <input
          type="email"
          name="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="rounded-md border border-black/20 bg-transparent px-3 py-2 text-base outline-none focus:border-blue-600 dark:border-white/25"
        />
      </label>

      <label className="flex flex-col gap-1 text-sm font-medium">
        Senha
        <input
          type="password"
          name="senha"
          autoComplete="current-password"
          required
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          className="rounded-md border border-black/20 bg-transparent px-3 py-2 text-base outline-none focus:border-blue-600 dark:border-white/25"
        />
      </label>

      <button
        type="submit"
        disabled={enviando}
        className="rounded-md bg-blue-600 px-4 py-2 font-semibold text-white transition hover:bg-blue-700 disabled:cursor-wait disabled:opacity-60"
      >
        {enviando ? "Entrando..." : "Entrar"}
      </button>

      <p role="alert" aria-live="polite" className="min-h-5 text-sm text-red-600 dark:text-red-400">
        {erro}
        {contaDeOutraArea && (
          <>
            {" "}
            {/* Link para outra aplicação: <a> comum, pois next/link aplicaria o basePath */}
            <a href="/aluno" className="underline">
              Acesse a área do aluno.
            </a>
          </>
        )}
      </p>
    </form>
  );
}
