"use client";

import { AreaAutenticada } from "./AreaAutenticada";

export function PainelInicial() {
  return (
    <AreaAutenticada>
      {(sessao) => (
        <main className="flex flex-1 flex-col gap-2 p-6">
          <h1 className="text-xl font-semibold">Olá, {sessao.usuario.nome.split(" ")[0]}</h1>
          <p className="text-sm opacity-70">Você entrou na área do aluno.</p>
        </main>
      )}
    </AreaAutenticada>
  );
}
