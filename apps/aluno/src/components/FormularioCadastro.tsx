"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ErroApi, registrar } from "@/lib/api";

const mensagensCampos: Record<string, string> = {
  nome: "Informe seu nome completo, com até 120 caracteres.",
  email: "Informe um e-mail válido, com até 254 caracteres.",
  matricula: "Informe sua matrícula, com até 30 caracteres.",
  senha: "A senha deve ter de 8 a 72 caracteres e não pode conter apenas espaços.",
};

const estiloCampo = "w-full rounded-lg border border-[#e2e8f0] bg-[#f8fafc] px-3 py-2.5 text-base text-[#172033] outline-none placeholder:text-[#64748b] focus:border-[#5040e8] focus:ring-2 focus:ring-[#5040e8]/20 disabled:opacity-60";

export function FormularioCadastro() {
  const router = useRouter();
  const envioEmAndamento = useRef(false);
  const [enviando, setEnviando] = useState(false);
  const [mostrarSenha, setMostrarSenha] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [camposInvalidos, setCamposInvalidos] = useState<Record<string, string>>({});

  async function cadastrar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (envioEmAndamento.current) return;
    const formulario = evento.currentTarget;
    const dados = new FormData(formulario);
    const cadastro = {
      nome: String(dados.get("nome") ?? "").trim(),
      email: String(dados.get("email") ?? "").trim(),
      matricula: String(dados.get("matricula") ?? "").trim(),
      senha: String(dados.get("senha") ?? ""),
    };
    const invalidos: Record<string, string> = {};
    for (const [campo, valor] of Object.entries(cadastro)) {
      if (!valor.trim()) invalidos[campo] = mensagensCampos[campo];
    }
    setErro(null);
    setCamposInvalidos(invalidos);
    if (Object.keys(invalidos).length > 0) {
      (formulario.elements.namedItem(Object.keys(invalidos)[0]) as HTMLInputElement)?.focus();
      return;
    }

    envioEmAndamento.current = true;
    setEnviando(true);
    try {
      await registrar(cadastro);
      // Limpa a senha antes de a rota ser preservada pelo Next.js.
      formulario.reset();
      setMostrarSenha(false);
      router.replace("/login?cadastro=sucesso");
    } catch (e) {
      const campos: Record<string, string> = {};
      if (e instanceof ErroApi && e.campos) {
        for (const campo of Object.keys(e.campos)) {
          if (mensagensCampos[campo]) campos[campo] = mensagensCampos[campo];
        }
      }
      setCamposInvalidos(campos);
      setErro(e instanceof ErroApi ? e.message : "Erro inesperado. Tente novamente.");
    } finally {
      envioEmAndamento.current = false;
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={cadastrar} aria-busy={enviando} className="mt-6">
      <fieldset disabled={enviando} className="flex min-w-0 flex-col gap-4">
        <legend className="sr-only">Dados da conta do aluno</legend>
        <label className="flex flex-col gap-1.5 text-sm font-medium">
          Nome completo
          <input name="nome" autoComplete="name" required maxLength={120} placeholder="Seu nome completo"
            aria-invalid={Boolean(camposInvalidos.nome)} aria-describedby={camposInvalidos.nome ? "erro-nome" : undefined}
            className={estiloCampo} />
          {camposInvalidos.nome && <span id="erro-nome" className="text-sm text-red-700">{camposInvalidos.nome}</span>}
        </label>
        <label className="flex flex-col gap-1.5 text-sm font-medium">
          E-mail
          <input type="email" name="email" autoComplete="email" required maxLength={254} placeholder="nome@instituicao.edu.br"
            aria-invalid={Boolean(camposInvalidos.email)} aria-describedby={camposInvalidos.email ? "erro-email" : undefined}
            className={estiloCampo} />
          {camposInvalidos.email && <span id="erro-email" className="text-sm text-red-700">{camposInvalidos.email}</span>}
        </label>
        <label className="flex flex-col gap-1.5 text-sm font-medium">
          Matrícula (RA)
          <input name="matricula" required maxLength={30} placeholder="Seu número de matrícula"
            aria-invalid={Boolean(camposInvalidos.matricula)} aria-describedby={camposInvalidos.matricula ? "erro-matricula" : undefined}
            className={estiloCampo} />
          {camposInvalidos.matricula && <span id="erro-matricula" className="text-sm text-red-700">{camposInvalidos.matricula}</span>}
        </label>
        <div className="flex flex-col gap-1.5 text-sm font-medium">
          <label htmlFor="senha">Crie uma senha forte</label>
          <div className="relative">
            <input id="senha" type={mostrarSenha ? "text" : "password"} name="senha" autoComplete="new-password"
              required minLength={8} maxLength={72} placeholder="De 8 a 72 caracteres"
              aria-invalid={Boolean(camposInvalidos.senha)} aria-describedby="ajuda-senha erro-senha"
              className={estiloCampo + " pr-24"} />
            <button type="button" onClick={() => setMostrarSenha(!mostrarSenha)} aria-controls="senha" aria-pressed={mostrarSenha}
              aria-label={mostrarSenha ? "Ocultar senha" : "Mostrar senha"}
              className="absolute inset-y-0 right-0 rounded-r-lg px-3 text-sm text-[#5040e8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5040e8]">
              {mostrarSenha ? "Ocultar" : "Mostrar"}
            </button>
          </div>
          <p id="ajuda-senha" className="text-xs font-normal text-[#64748b]">Use de 8 a 72 caracteres.</p>
          <p id="erro-senha" className="text-sm text-red-700">{camposInvalidos.senha}</p>
        </div>
        
        <p role="alert" aria-live="polite" className="text-sm text-red-700">{erro}</p>
        <button type="submit"
          className="min-h-11 rounded-lg bg-[#5040e8] px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-[#4332cd] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5040e8] disabled:cursor-wait disabled:opacity-60">
          {enviando ? "Cadastrando..." : "Concluir meu cadastro"}
        </button>
      </fieldset>
    </form>
  );
}

