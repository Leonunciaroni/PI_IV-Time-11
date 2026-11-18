// Aparência compartilhada de botões e cartões, a partir dos tokens de globals.css. O tamanho
// (padding) fica em cada tela; aqui ficam cor, borda, cantos e tipografia.

/**
 * Alvo de toque no celular: altura mínima de 40 px, com o texto centralizado. Só vale abaixo do
 * breakpoint sm (640 px); no desktop o tamanho segue o padding de cada tela.
 */
export const alvoDeToque = "max-sm:inline-flex max-sm:min-h-10 max-sm:items-center max-sm:justify-center";

/** Ação principal: botão ou link com cara de botão. */
export const botaoPrincipal = `${alvoDeToque} rounded-controle bg-primaria font-semibold text-white shadow-sm shadow-primaria/30 transition hover:bg-primaria-forte disabled:cursor-wait disabled:opacity-60`;

/** Ação secundária, com contorno. No hover, contorno e fundo na cor principal (exceto desabilitado). */
export const botaoSecundario = `${alvoDeToque} rounded-controle border border-borda-forte bg-superficie transition not-disabled:hover:border-primaria not-disabled:hover:bg-primaria/10 disabled:cursor-not-allowed disabled:opacity-50`;

/** Ação que não tem volta, como encerrar a chamada: contorno para abrir a confirmação. */
export const botaoPerigoContorno = `${alvoDeToque} rounded-controle border border-perigo font-semibold text-perigo disabled:cursor-not-allowed disabled:opacity-50`;

/** Confirmação de uma ação que não tem volta. */
export const botaoPerigo = `${alvoDeToque} rounded-controle bg-perigo-botao font-semibold text-white disabled:cursor-wait disabled:opacity-60`;

/** Bloco com borda, como o cartão de turma. */
export const cartao = "rounded-cartao border border-borda bg-superficie";
