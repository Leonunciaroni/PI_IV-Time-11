import type { Metadata } from "next";
import { Suspense } from "react";
import { ListaTurmas } from "@/components/ListaTurmas";

export const metadata: Metadata = { title: "Minhas turmas — Fast Attendance" };

export default function Inicio() {
  return (
    <Suspense fallback={null}>
      <ListaTurmas />
    </Suspense>
  );
}
