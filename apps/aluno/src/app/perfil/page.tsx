// Guilherme Preventi Correia
// RA: 25001694

import type { Metadata } from "next";
import { Suspense } from "react";
import { Perfil } from "@/components/Perfil";

export const metadata: Metadata = { title: "Meu perfil — Fast Attendance" };

export default function PaginaPerfil() {
  return <Suspense fallback={null}><Perfil /></Suspense>;
}
