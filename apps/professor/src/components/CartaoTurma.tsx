import Link from "next/link";
import type { Turma } from "@/lib/tipos";

export function CartaoTurma({ turma }: { turma: Turma }) {
  const alunos = turma.totalAlunos === 1 ? "1 aluno" : `${turma.totalAlunos} alunos`;

  return (
    <li className="flex flex-col gap-3 rounded-lg border border-black/15 p-4 dark:border-white/20">
      <div>
        <h2 className="font-semibold">{turma.disciplina}</h2>
        <p className="text-sm opacity-80">
          {turma.nome} · {turma.horarioPadrao}
        </p>
        <p className="text-sm opacity-80">{alunos}</p>
      </div>
      <div className="flex gap-2 text-sm">
        {/* A abertura de chamada depende do servidor de sessão (P5) */}
        <button
          type="button"
          disabled
          className="rounded-md border border-black/20 px-3 py-1 disabled:cursor-not-allowed disabled:opacity-50 dark:border-white/25"
        >
          Abrir chamada
        </button>
        <Link
          href={`/turmas/${turma.id}`}
          className="rounded-md border border-black/20 px-3 py-1 dark:border-white/25"
        >
          Detalhes
        </Link>
      </div>
    </li>
  );
}
