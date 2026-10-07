import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Mesmo caminho usado no Nginx: a aplicação, a API (/api) e o WebSocket (/ws)
  // compartilham a mesma origem.
  basePath: "/aluno",
  // Gera .next/standalone com server.js para a imagem Docker.
  output: "standalone",
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
