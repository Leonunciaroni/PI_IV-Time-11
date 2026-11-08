import type { Metadata } from "next";
import { Suspense } from "react";
import { AbrirChamada } from "@/components/AbrirChamada";

export const metadata: Metadata = { title: "Abrir chamada — Fast Attendance" };

export default function Pagina() {
  return (
    <Suspense fallback={null}>
      <AbrirChamada />
    </Suspense>
  );
}
