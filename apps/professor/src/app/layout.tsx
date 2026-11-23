/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
import type { Metadata } from "next";
import { Atkinson_Hyperlegible_Next, Geist_Mono } from "next/font/google";
import localFont from "next/font/local";
import "./globals.css";

// A mesma fonte do app do aluno: feita para leitura rápida e sem confundir letras parecidas
// (I, l, 1), útil para conferir nomes e matrículas na lista de chamada.
const fonteTexto = Atkinson_Hyperlegible_Next({
  variable: "--font-atkinson",
  subsets: ["latin", "latin-ext"],
});

// Só os algarismos 0–9 vêm da Geist: o zero da Atkinson tem um corte, e a fonte não traz um
// zero alternativo. O arquivo é o que o próprio Next distribui; se ele mudar de lugar numa
// atualização do Next, o build falha aqui.
const fonteDigitos = localFont({
  src: [{ path: "../../node_modules/next/dist/next-devtools/server/font/geist-latin.woff2", weight: "100 900" }],
  variable: "--font-digitos",
  declarations: [{ prop: "unicode-range", value: "U+0030-0039" }],
  adjustFontFallback: false,
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Fast Attendance — Professor",
  description: "Abertura de chamada e acompanhamento de presença em tempo real.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      className={`${fonteTexto.variable} ${fonteDigitos.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
