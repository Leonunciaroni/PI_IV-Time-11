/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Identificador aleatório do navegador do aluno, enviado no check-in (campo dispositivoId do
// protocolo). Impede que um mesmo aparelho registre presença para dois alunos na mesma sessão.
// Não identifica o aparelho fora do sistema (LGPD).

const CHAVE = "fa.aluno.dispositivoId";

function uuidV4(): string {
  // crypto.randomUUID só existe em contexto seguro (HTTPS/localhost); getRandomValues existe sempre.
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** Devolve o identificador do dispositivo, criando-o na primeira execução. Só no navegador. */
export function obterDispositivoId(): string {
  let id = localStorage.getItem(CHAVE);
  if (!id) {
    id = uuidV4();
    localStorage.setItem(CHAVE, id);
  }
  return id;
}
