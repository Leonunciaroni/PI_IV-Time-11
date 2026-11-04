import { Suspense } from "react";
import { ListaTurmas } from "@/components/ListaTurmas";

export default function Inicio() {
  return (
    <Suspense fallback={null}>
      <ListaTurmas />
    </Suspense>
  );
}
