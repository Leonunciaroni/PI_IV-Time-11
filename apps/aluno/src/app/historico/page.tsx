import { Suspense } from "react";
import type { Metadata } from "next";
import { HistoricoFrequencia } from "@/components/HistoricoFrequencia";

export const metadata: Metadata = {
  title: "Histórico de frequência — Fast Attendance",
};

export default function PaginaHistorico() {
  return (
    <Suspense fallback={<p role="status">Carregando histórico...</p>}>
      <HistoricoFrequencia />
    </Suspense>
  );
}
