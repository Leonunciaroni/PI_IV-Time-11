// Guilherme Preventi Correia
// RA: 25001694

"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { AreaAutenticada } from "./AreaAutenticada";
import { editarPerfil, ErroPerfil, validarSenha, type AlteracaoPerfil } from "@/lib/perfil";
import { atualizarUsuario, type Sessao } from "@/lib/sessao";

const campo = "mt-2 w-full min-w-0 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-base text-[#172033] outline-none focus:border-[#5040e8] focus:ring-2 focus:ring-[#5040e8]/20";
const botao = "min-h-11 rounded-lg bg-[#5040e8] px-5 py-3 text-sm font-semibold text-white hover:bg-[#4332cd] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5040e8] disabled:opacity-50";

export function Perfil() {
  return <AreaAutenticada>{(sessao) => <FormularioPerfil key={sessao.token} sessao={sessao} />}</AreaAutenticada>;
}

function FormularioPerfil({ sessao }: { sessao: Sessao }) {
  const [nome, setNome] = useState(sessao.usuario.nome);
  const [email, setEmail] = useState(sessao.usuario.email);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [sucesso, setSucesso] = useState<string | null>(null);
  const controle = useRef<AbortController | null>(null);
  const ocupado = useRef(false);
  const senha = useRef<HTMLFormElement>(null);

  useEffect(() => {
    const formularioSenha = senha.current;
    return () => {
      controle.current?.abort();
      ocupado.current = false;
      formularioSenha?.reset();
    };
  }, []);

  async function salvar(dados: AlteracaoPerfil, formulario: HTMLFormElement) {
    if (ocupado.current) return;
    ocupado.current = true;
    const operacao = new AbortController();
    controle.current = operacao;
    setEnviando(true);
    setErro(null);
    setCampos({});
    setSucesso(null);
    try {
      const usuario = await editarPerfil(sessao.token, dados, operacao.signal);
      if (operacao.signal.aborted) return;
      try {
        if (!atualizarUsuario(usuario, sessao.token)) return;
      } catch {
        throw new ErroPerfil("Os dados foram salvos, mas o navegador não conseguiu atualizar sua sessão. Saia e entre novamente.");
      }
      setNome(usuario.nome);
      setEmail(usuario.email);
      if ("novaSenha" in dados) formulario.reset();
      setSucesso("novaSenha" in dados ? "Senha alterada com sucesso." : "Perfil atualizado com sucesso.");
    } catch (e) {
      if (operacao.signal.aborted) return;
      setErro(e instanceof ErroPerfil ? e.message : "Não foi possível salvar as alterações. Tente novamente.");
      setCampos(e instanceof ErroPerfil ? e.campos : {});
    } finally {
      ocupado.current = false;
      // Também libera o formulário preservado quando o Next oculta a rota.
      setEnviando(false);
    }
  }

  function salvarDados(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!nome.trim()) {
      setErro("Informe seu nome completo.");
      setCampos({ nome: "O nome não pode ficar vazio." });
      setSucesso(null);
      return;
    }
    void salvar({ nome: nome.trim(), email: email.trim() }, evento.currentTarget);
  }

  function salvarSenha(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const dados = new FormData(evento.currentTarget);
    const senhaAtual = String(dados.get("senhaAtual") ?? "");
    const novaSenha = String(dados.get("novaSenha") ?? "");
    try {
      validarSenha(senhaAtual, novaSenha, String(dados.get("confirmacao") ?? ""));
    } catch (e) {
      if (e instanceof ErroPerfil) { setErro(e.message); setCampos(e.campos); setSucesso(null); }
      return;
    }
    void salvar({ senhaAtual, novaSenha }, evento.currentTarget);
  }

  function mensagem(campo: string) {
    return campos[campo] ? <span id={`erro-${campo}`} className="mt-1 block text-xs text-red-700">{campos[campo]}</span> : null;
  }

  return (
    <main className="w-full flex-1 bg-[#f8fafc] px-5 py-8 text-[#172033] sm:px-10">
      <div className="mx-auto max-w-2xl">
        <Link href="/" className="text-sm font-semibold text-[#5040e8] hover:underline">← Voltar ao início</Link>
        <h1 className="mt-6 text-3xl font-bold tracking-tight">Meu perfil</h1>
        <p className="mt-2 text-sm text-slate-500">Atualize seus dados e mantenha sua conta segura.</p>
        <p role="alert" className={erro ? "mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800" : "sr-only"}>{erro}</p>
        <p role="status" className={sucesso ? "mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900" : "sr-only"}>{sucesso}</p>
        <form onSubmit={salvarDados} aria-busy={enviando} className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 sm:p-7">
          <fieldset disabled={enviando} className="min-w-0 space-y-5">
            <legend className="mb-5 text-lg font-semibold">Dados pessoais</legend>
            <div className="text-sm font-medium"><label htmlFor="perfil-nome">Nome completo</label>
              <input id="perfil-nome" name="nome" autoComplete="name" required maxLength={120} value={nome} onChange={e => setNome(e.target.value)} aria-invalid={Boolean(campos.nome)} aria-describedby={campos.nome ? "erro-nome" : undefined} className={campo} />
              {mensagem("nome")}
            </div>
            <div className="text-sm font-medium"><label htmlFor="perfil-email">E-mail</label>
              <input id="perfil-email" name="email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)} aria-invalid={Boolean(campos.email)} aria-describedby={campos.email ? "erro-email" : undefined} className={campo} />
              {mensagem("email")}
            </div>
            <p className="break-words text-sm text-slate-500">Matrícula / registro funcional: {sessao.usuario.matricula}</p>
            <button type="submit" className={botao}>{enviando ? "Salvando..." : "Salvar alterações"}</button>
          </fieldset>
        </form>
        <form ref={senha} onSubmit={salvarSenha} aria-busy={enviando} className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 sm:p-7">
          <fieldset disabled={enviando} className="min-w-0 space-y-5">
            <legend className="mb-5 text-lg font-semibold">Alterar senha</legend>
            <div className="text-sm font-medium"><label htmlFor="perfil-senha-atual">Senha atual</label>
              <input id="perfil-senha-atual" name="senhaAtual" type="password" autoComplete="current-password" required maxLength={72} aria-invalid={Boolean(campos.senhaAtual)} aria-describedby={campos.senhaAtual ? "erro-senhaAtual" : undefined} className={campo} />
              {mensagem("senhaAtual")}
            </div>
            <div className="text-sm font-medium"><label htmlFor="perfil-nova-senha">Nova senha</label>
              <input id="perfil-nova-senha" name="novaSenha" type="password" autoComplete="new-password" required minLength={8} maxLength={72} aria-invalid={Boolean(campos.novaSenha)} aria-describedby={campos.novaSenha ? "ajuda-senha erro-novaSenha" : "ajuda-senha"} className={campo} />
              <span id="ajuda-senha" className="mt-1 block text-xs font-normal text-slate-500">Use de 8 a 72 caracteres. Muitos acentos ou emojis podem exigir uma senha mais curta.</span>
              {mensagem("novaSenha")}
            </div>
            <div className="text-sm font-medium"><label htmlFor="perfil-confirmacao">Confirme a nova senha</label>
              <input id="perfil-confirmacao" name="confirmacao" type="password" autoComplete="new-password" required minLength={8} maxLength={72} aria-invalid={Boolean(campos.confirmacao)} aria-describedby={campos.confirmacao ? "erro-confirmacao" : undefined} className={campo} />
              {mensagem("confirmacao")}
            </div>
            <button type="submit" className={botao}>{enviando ? "Salvando..." : "Alterar senha"}</button>
          </fieldset>
        </form>
      </div>
    </main>
  );
}
