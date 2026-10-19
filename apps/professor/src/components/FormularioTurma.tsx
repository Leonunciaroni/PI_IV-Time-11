"use client";

import { useState, type SubmitEvent } from "react";
import { ErroApi } from "@/lib/api";
import { Botao } from "./ui/Botao";
import { Campo } from "./ui/Campo";
import { MensagemDeErro } from "./ui/MensagemDeErro";

export interface DadosTurma {
  nome: string;
  disciplina: string;
  horarioPadrao: string;
}

type Erros = Partial<Record<keyof DadosTurma, string>>;

/** Validação do lado do cliente; a API continua sendo a fonte final das regras. */
export function validarTurma(dados: DadosTurma): Erros {
  const erros: Erros = {};
  if (!dados.nome.trim()) erros.nome = "Informe o nome da turma.";
  if (!dados.disciplina.trim()) erros.disciplina = "Informe a disciplina.";
  if (!dados.horarioPadrao.trim()) erros.horarioPadrao = "Informe o horário padrão.";
  return erros;
}

interface FormularioTurmaProps {
  /** Envia a turma. Enquanto não for informado (API pendente), o formulário só valida. */
  aoEnviar?: (dados: DadosTurma) => Promise<void>;
}

export function FormularioTurma({ aoEnviar }: FormularioTurmaProps) {
  const [dados, setDados] = useState<DadosTurma>({ nome: "", disciplina: "", horarioPadrao: "" });
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function alterar(campo: keyof DadosTurma, valor: string) {
    setDados((atual) => ({ ...atual, [campo]: valor }));
    // Some com o erro do campo assim que o usuário volta a digitar nele
    setErros((atuais) => ({ ...atuais, [campo]: undefined }));
  }

  async function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErroGeral(null);

    const limpos: DadosTurma = {
      nome: dados.nome.trim(),
      disciplina: dados.disciplina.trim(),
      horarioPadrao: dados.horarioPadrao.trim(),
    };
    const encontrados = validarTurma(limpos);
    setErros(encontrados);
    if (Object.keys(encontrados).length > 0) return;
    if (!aoEnviar) {
      setErroGeral("O cadastro será liberado quando a API de turmas estiver disponível.");
      return;
    }

    setEnviando(true);
    try {
      await aoEnviar(limpos);
    } catch (e) {
      setErroGeral(e instanceof ErroApi ? e.message : "Erro inesperado. Tente novamente.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
      <Campo
        rotulo="Nome da turma"
        name="nome"
        placeholder="Turma A"
        value={dados.nome}
        onChange={(e) => alterar("nome", e.target.value)}
        erro={erros.nome}
      />

      <Campo
        rotulo="Disciplina"
        name="disciplina"
        placeholder="Engenharia de Software I"
        value={dados.disciplina}
        onChange={(e) => alterar("disciplina", e.target.value)}
        erro={erros.disciplina}
      />

      <Campo
        rotulo="Horário padrão"
        name="horarioPadrao"
        placeholder="Seg 19:00"
        value={dados.horarioPadrao}
        onChange={(e) => alterar("horarioPadrao", e.target.value)}
        erro={erros.horarioPadrao}
      />

      <Botao type="submit" disabled={enviando}>
        {enviando ? "Salvando..." : "Criar turma"}
      </Botao>

      <MensagemDeErro>{erroGeral}</MensagemDeErro>
    </form>
  );
}
