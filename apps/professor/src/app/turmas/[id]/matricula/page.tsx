/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import type { Metadata } from "next";
import { Suspense } from "react";
import { MatriculaAlunos } from "@/components/MatriculaAlunos";

export const metadata: Metadata = { title: "Matricular alunos — Fast Attendance" };

export default function Pagina() {
  return (
    <Suspense fallback={null}>
      <MatriculaAlunos />
    </Suspense>
  );
}
