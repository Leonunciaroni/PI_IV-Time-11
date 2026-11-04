// Aparência compartilhada de botões e cartões, a partir dos tokens de globals.css. O tamanho
// (padding) fica em cada tela; aqui ficam cor, borda, cantos e tipografia.

/** Ação principal: botão ou link com cara de botão. */
export const botaoPrincipal =
  "rounded-controle bg-primaria font-semibold text-white transition hover:bg-primaria-forte disabled:cursor-wait disabled:opacity-60";

/** Ação secundária, com contorno. */
export const botaoSecundario =
  "rounded-controle border border-borda-forte disabled:cursor-not-allowed disabled:opacity-50";

/** Bloco com borda, como o cartão de turma. */
export const cartao = "rounded-cartao border border-borda";
