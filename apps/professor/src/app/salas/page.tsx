/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import type { Metadata } from "next";
import { Suspense } from "react";
import { Salas } from "@/components/Salas";

export const metadata: Metadata = { title: "Salas — Fast Attendance" };

export default function Pagina() {
  return (
    <Suspense fallback={null}>
      <Salas />
    </Suspense>
  );
}
