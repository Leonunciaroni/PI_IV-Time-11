import Link from "next/link";
import type { Turma } from "@/lib/tipos";
import { botaoPrincipal, botaoSecundario, cartao } from "./ui/estilos";

export function CartaoTurma({ turma }: { turma: Turma }) {
  const alunos = turma.totalAlunos === 1 ? "1 aluno" : `${turma.totalAlunos} alunos`;

  return (
    <li className={`${cartao} flex flex-col gap-3 p-4`}>
      <div>
        <h2 className="font-semibold">{turma.disciplina}</h2>
        <p className="text-sm opacity-80">
          {turma.nome} · {turma.horarioPadrao}
        </p>
        <p className="text-sm opacity-80">{alunos}</p>
      </div>
      <div className="flex gap-2 text-sm">
        <Link href={`/turmas/${turma.id}/chamada`} className={`${botaoPrincipal} px-3 py-1`}>
          Abrir chamada
        </Link>
        <Link href={`/turmas/${turma.id}`} className={`${botaoSecundario} px-3 py-1`}>
          Detalhes
        </Link>
      </div>
    </li>
  );
}
