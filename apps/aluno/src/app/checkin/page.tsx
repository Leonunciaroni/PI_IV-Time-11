// Guilherme Preventi Correia
// RA: 25001694

import { Suspense } from "react";
import type { Metadata } from "next";
import { EntradaCheckin } from "@/components/EntradaCheckin";

export const metadata: Metadata = {
  title: "Confirmar presença — Fast Attendance",
};

export default function PaginaCheckin() {
  return (
    <Suspense fallback={<p role="status">Carregando chamada...</p>}>
      <EntradaCheckin />
    </Suspense>
  );
}
