/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Guilherme Preventi Correia
// RA: 25001694

import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Mesmo caminho usado no Nginx: a aplicação, a API (/api) e o WebSocket (/ws)
  // compartilham a mesma origem.
  basePath: "/professor",
  // Gera .next/standalone com server.js para a imagem Docker.
  output: "standalone",
  // Esconde o botão "N" do Next no canto da tela em `npm run dev`, para capturas e gravações
  // locais saírem limpas. Os erros de compilação e de execução continuam aparecendo. Em
  // produção (build e Docker) o indicador nunca aparece.
  devIndicators: false,
  async redirects() {
    // No dev, aceita os endereços sem prefixo abertos pelo terminal ou digitados à mão.
    if (process.env.NODE_ENV !== "development") return [];
    return ["/", "/login", "/cadastro"].map((caminho) => ({
      source: caminho,
      destination: `/professor${caminho}`,
      basePath: false,
      permanent: false,
    }));
  },
  async rewrites() {
    // Em produção o Nginx atende /api na mesma origem. Em desenvolvimento (npm run dev),
    // encaminha /api para o ambiente do Docker Compose.
    if (process.env.NODE_ENV !== "development") return [];
    const api = process.env.API_URL ?? "http://localhost:8088";
    return [{ source: "/api/:path*", destination: `${api}/api/:path*`, basePath: false }];
  },
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
