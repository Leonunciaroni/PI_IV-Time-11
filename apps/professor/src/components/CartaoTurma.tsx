/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import Link from "next/link";
import type { Turma } from "@/lib/tipos";
import { botaoPrincipal, botaoSecundario, cartao } from "./ui/estilos";

export function CartaoTurma({ turma }: { turma: Turma }) {
  const alunos =
    turma.totalAlunos === 0
      ? "Nenhum aluno matriculado"
      : turma.totalAlunos === 1
        ? "1 aluno"
        : `${turma.totalAlunos} alunos`;
  // Os links se repetem em todos os cartões; o nome da turma diferencia cada um no leitor de tela
  const nomeCompleto = `${turma.disciplina} · ${turma.nome}`;

  return (
    <li className={`${cartao} flex flex-col gap-3 p-4`}>
      <div className="min-w-0">
        <h2 className="font-semibold break-words">{turma.disciplina}</h2>
        <p className="text-sm text-suave">
          {turma.nome} · {turma.horarioPadrao}
        </p>
        <p className="text-sm text-suave">{alunos}</p>
      </div>
      {/* mt-auto: os botões ficam na mesma altura mesmo quando um cartão da linha tem título maior */}
      <div className="mt-auto flex flex-wrap gap-2 text-sm">
        <Link
          href={`/turmas/${turma.id}/chamada`}
          aria-label={`Abrir chamada de ${nomeCompleto}`}
          className={`${botaoPrincipal} px-3 py-1`}
        >
          Abrir chamada
        </Link>
        <Link
          href={`/turmas/${turma.id}`}
          aria-label={`Detalhes de ${nomeCompleto}`}
          className={`${botaoSecundario} px-3 py-1`}
        >
          Detalhes
        </Link>
      </div>
    </li>
  );
}
