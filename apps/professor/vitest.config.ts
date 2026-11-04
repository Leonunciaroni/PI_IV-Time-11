import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

// Testes unitários da lógica sem interface (lib/), ao lado do código: src/**/*.test.ts
export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    include: ["src/**/*.test.ts"],
    environment: "node",
  },
});
