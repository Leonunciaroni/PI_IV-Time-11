import { Suspense } from "react";
import { PainelInicial } from "@/components/PainelInicial";

export default function Inicio() {
  return (
    <Suspense fallback={null}>
      <PainelInicial />
    </Suspense>
  );
}
