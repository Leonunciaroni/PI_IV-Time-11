import type { Metadata } from "next";
import { Suspense } from "react";
import { NovaTurma } from "@/components/NovaTurma";

export const metadata: Metadata = {
  title: "Nova turma — Fast Attendance",
};

export default function PaginaNovaTurma() {
  return (
    <Suspense fallback={null}>
      <NovaTurma />
    </Suspense>
  );
}
