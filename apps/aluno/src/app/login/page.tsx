// Guilherme Preventi Correia
// RA: 25001694

import type { Metadata } from "next";
import { Suspense } from "react";
import { FormularioLogin } from "@/components/FormularioLogin";
import { Logo } from "@/components/ui/Logo";

export const metadata: Metadata = {
  title: "Entrar — Fast Attendance",
};

export default function PaginaLogin() {
  return (
    <main className="flex flex-1 items-center justify-center p-4">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <header className="text-center">
          <h1 className="text-2xl">
            <Logo />
          </h1>
          <p className="text-sm opacity-70">Área do aluno</p>
        </header>

        <Suspense fallback={<div className="h-64" aria-busy="true" />}>
          <FormularioLogin />
        </Suspense>

        <p className="text-center text-sm opacity-70">
          É professor?{" "}
          <a href="/professor" className="underline">
            Acesse a área do professor
          </a>
        </p>
      </div>
    </main>
  );
}
