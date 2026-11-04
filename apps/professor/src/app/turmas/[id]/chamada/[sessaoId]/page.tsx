import type { Metadata } from "next";
import { Suspense } from "react";
import { ChamadaEmAndamento } from "@/components/ChamadaEmAndamento";

export const metadata: Metadata = { title: "Chamada — Fast Attendance" };

export default function Pagina() {
  return (
    <Suspense fallback={null}>
      <ChamadaEmAndamento />
    </Suspense>
  );
}
