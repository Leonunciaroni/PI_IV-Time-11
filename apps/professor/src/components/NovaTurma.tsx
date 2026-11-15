"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { enviar } from "@/lib/api";
import type { Turma } from "@/lib/tipos";
import { AreaAutenticada } from "./AreaAutenticada";
import { FormularioTurma, type DadosTurma } from "./FormularioTurma";

export function NovaTurma() {
  const router = useRouter();

  async function criar(dados: DadosTurma) {
    const turma = await enviar<Turma>("/api/turmas", dados);
    // A lista confirma o cadastro a partir do id
    router.push(`/?criada=${encodeURIComponent(turma.id)}`);
  }

  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-6 p-6">
          <div>
            <Link href="/" className="text-sm underline">
              ← Minhas turmas
            </Link>
            <h1 className="mt-2 text-xl font-semibold">Nova turma</h1>
            <p className="text-sm opacity-70">Todos os campos são obrigatórios.</p>
          </div>
          <FormularioTurma aoEnviar={criar} />
        </main>
      )}
    </AreaAutenticada>
  );
}
