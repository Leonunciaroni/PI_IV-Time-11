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
          <p className="text-sm text-suave">Área do professor</p>
        </header>

        <Suspense fallback={<div className="h-64" aria-busy="true" />}>
          <FormularioLogin />
        </Suspense>

        <p className="text-center text-sm text-suave">
          É aluno?{" "}
          <a href="/aluno" className="underline">
            Acesse a área do aluno
          </a>
        </p>
      </div>
    </main>
  );
}
