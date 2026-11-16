// Tipos do protocolo WebSocket com o servidor de sessão (docs/protocolo-v1.md), espelhando
// common/.../protocolo. Só o que o app do professor usa.

export const VERSAO_PROTOCOLO = 1;
export const INTERVALO_PING_MS = 25_000;

/** Códigos de fechamento do WebSocket (seção 8). */
export const Fechamento = {
  NORMAL: 1000,
  SERVIDOR_REINICIANDO: 1001,
  AUTENTICACAO_FALHOU: 4001,
  VERSAO_NAO_SUPORTADA: 4002,
  TOKEN_EXPIROU: 4003,
  VIOLACAO_POLITICA: 4008,
} as const;

export type CodigoErro =
  | "MENSAGEM_INVALIDA"
  | "VERSAO_NAO_SUPORTADA"
  | "AUTH_REQUERIDA"
  | "AUTH_INVALIDA"
  | "PERMISSAO_NEGADA"
  | "SESSAO_NAO_ENCONTRADA"
  | "SESSAO_ENCERRADA"
  | "SESSAO_JA_ABERTA"
  | "MODO_INCOMPATIVEL"
  | "TOKEN_INVALIDO"
  | "TOKEN_EXPIRADO"
  | "FORA_DO_RAIO"
  | "ALUNO_NAO_MATRICULADO"
  | "PRESENCA_DUPLICADA"
  | "DISPOSITIVO_JA_UTILIZADO"
  | "LIMITE_EXCEDIDO"
  | "ERRO_INTERNO";

export type ModoChamada = "CLASSICA" | "QRCODE" | "SOM";

/** Envelope de toda mensagem, nas duas direções (seção 2). */
export interface Envelope<T = unknown> {
  v: number;
  tipo: string;
  id?: string;
  ref?: string | null;
  ts: number;
  dados: T;
}

/** `dados` de uma resposta `erro`. O código chega como texto: o servidor pode ter códigos novos. */
export interface Erro {
  codigo: CodigoErro | (string & {});
  mensagem: string;
}

export interface AuthOk {
  usuarioId: string;
  nome: string;
  papel: "PROFESSOR" | "ALUNO";
  expiraEm: number;
}

export interface ConfiguracaoTotp {
  segredo: string;
  algoritmo: string;
  digitos: number;
  periodoSegundos: number;
  t0: number;
}

export interface SessaoAbrir {
  turmaId: string;
  modo: ModoChamada;
  salaId?: string;
}

export interface SessaoAberta {
  sessaoId: string;
  turmaId: string;
  modo: ModoChamada;
  abertaEm: number;
  totp: ConfiguracaoTotp;
}

export interface SessaoEncerrada {
  sessaoId: string;
  motivo: "PROFESSOR" | "TEMPO_LIMITE";
  encerradaEm: number;
  totalPresentes: number;
}

export type OrigemPresenca = "MANUAL" | "QRCODE" | "SOM";

/** Resposta de presenca.marcar (seção 4.6), igual ao evento da seção 5.2. */
export interface PresencaAtualizada {
  sessaoId: string;
  alunoId: string;
  nomeAluno: string;
  presente: boolean;
  origem: OrigemPresenca;
  registradoEm: number;
  totalPresentes: number;
  totalMatriculados: number;
}

/** Item de `presentes` em sessao.estado: só quem está com presença confirmada. */
export interface PresenteNaSessao {
  alunoId: string;
  nome: string;
  origem: OrigemPresenca;
  registradoEm: number;
}

/** Resposta de sessao.acompanhar (seções 4.5 e 5.1): o estado atual da sessão aberta. */
export interface SessaoEstado {
  sessaoId: string;
  modo: ModoChamada;
  abertaEm: number;
  presentes: PresenteNaSessao[];
  totalMatriculados: number;
}

/** Resposta de presenca.marcar_lote (seção 4.7). */
export interface PresencaLoteOk {
  sessaoId: string;
  totalPresentes: number;
  totalMatriculados: number;
}

const MENSAGENS: Record<CodigoErro, string> = {
  MENSAGEM_INVALIDA: "O servidor não entendeu o pedido. Recarregue a página e tente de novo.",
  VERSAO_NAO_SUPORTADA: "Esta versão do app está desatualizada. Recarregue a página.",
  AUTH_REQUERIDA: "Sua sessão expirou. Entre novamente.",
  AUTH_INVALIDA: "Sua sessão expirou. Entre novamente.",
  PERMISSAO_NEGADA: "Você não tem permissão para esta chamada. Confira se a turma é sua.",
  SESSAO_NAO_ENCONTRADA: "Esta chamada não existe mais.",
  SESSAO_ENCERRADA: "Esta chamada já foi encerrada.",
  SESSAO_JA_ABERTA: "Já existe uma chamada aberta para esta turma.",
  MODO_INCOMPATIVEL: "Esta ação não vale para o modo desta chamada.",
  TOKEN_INVALIDO: "O código não confere.",
  TOKEN_EXPIRADO: "O código expirou.",
  FORA_DO_RAIO: "O aluno está longe demais da sala.",
  ALUNO_NAO_MATRICULADO: "Um dos alunos não está matriculado nesta turma.",
  PRESENCA_DUPLICADA: "Esta presença já foi registrada.",
  DISPOSITIVO_JA_UTILIZADO: "Este aparelho já registrou a presença de outro aluno.",
  LIMITE_EXCEDIDO: "Muitas ações em pouco tempo. Aguarde alguns segundos.",
  ERRO_INTERNO: "O servidor de chamada teve um erro. Tente de novo.",
};

/** Frase para o professor a partir de um `erro` do servidor, cuja mensagem é técnica. */
export function mensagemDoErro(erro: Erro): string {
  if (erro.codigo === "SESSAO_ENCERRADA" && /tempo limite/i.test(erro.mensagem)) {
    return "Esta chamada foi encerrada automaticamente por tempo limite.";
  }
  if (erro.codigo in MENSAGENS) return MENSAGENS[erro.codigo as CodigoErro];
  return erro.mensagem || MENSAGENS.ERRO_INTERNO;
}
