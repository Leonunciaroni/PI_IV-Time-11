import type { Metadata } from "next";
import { Suspense } from "react";
import { DetalhesTurma } from "@/components/DetalhesTurma";

export const metadata: Metadata = { title: "Detalhes da turma — Fast Attendance" };

export default function Pagina() {
  return (
    <Suspense fallback={null}>
      <DetalhesTurma />
    </Suspense>
  );
}
