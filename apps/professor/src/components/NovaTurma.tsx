"use client";

import Link from "next/link";
import { AreaAutenticada } from "./AreaAutenticada";
import { FormularioTurma } from "./FormularioTurma";

export function NovaTurma() {
  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-6 p-6">
          <div>
            <Link href="/" className="text-sm underline">
              ← Minhas turmas
            </Link>
            <h1 className="mt-2 text-xl font-semibold">Nova turma</h1>
          </div>
          <FormularioTurma />
        </main>
      )}
    </AreaAutenticada>
  );
}
