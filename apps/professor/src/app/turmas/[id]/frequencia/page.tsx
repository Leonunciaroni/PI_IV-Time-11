import type { Metadata } from "next";
import { Suspense } from "react";
import { DashboardTurma } from "@/components/DashboardTurma";

export const metadata: Metadata = { title: "Frequência da turma — Fast Attendance" };

export default function Pagina() {
  return (
    <Suspense fallback={null}>
      <DashboardTurma />
    </Suspense>
  );
}
