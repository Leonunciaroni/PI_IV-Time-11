// Protocolo v1 do servidor de sessão, compartilhando o contrato usado pelo professor.
export const VERSAO_PROTOCOLO = 1;
export const INTERVALO_PING_MS = 25_000;
export const Fechamento = {
  NORMAL: 1000,
  SERVIDOR_REINICIANDO: 1001,
  AUTENTICACAO_FALHOU: 4001,
  VERSAO_NAO_SUPORTADA: 4002,
  TOKEN_EXPIROU: 4003,
  VIOLACAO_POLITICA: 4008,
} as const;

export interface Envelope<T = unknown> {
  v: number;
  tipo: string;
  id?: string;
  ref?: string | null;
  ts: number;
  dados: T;
}
export interface Erro {
  codigo: string;
  mensagem: string;
}
export interface CheckinOk {
  sessaoId: string;
  registradoEm: number;
}

export const errosCheckin = {
  MENSAGEM_INVALIDA:
    "Não foi possível enviar os dados da presença. Recarregue a página e tente novamente.",
  VERSAO_NAO_SUPORTADA:
    "Esta versão do app está desatualizada. Recarregue a página.",
  AUTH_REQUERIDA: "Sua sessão expirou. Entre novamente.",
  AUTH_INVALIDA: "Sua sessão expirou. Entre novamente.",
  PERMISSAO_NEGADA: "Use uma conta de aluno autorizada para esta chamada.",
  SESSAO_NAO_ENCONTRADA:
    "Esta chamada não foi encontrada. Volte ao início e atualize suas turmas.",
  SESSAO_ENCERRADA:
    "Esta chamada já foi encerrada. Não é mais possível confirmar presença.",
  SESSAO_JA_ABERTA:
    "Já existe uma chamada aberta para esta turma. Atualize suas turmas.",
  MODO_INCOMPATIVEL:
    "O modo da chamada mudou. Volte ao início e escolha o canal indicado pelo professor.",
  TOKEN_INVALIDO:
    "O código não confere. Leia novamente o QR Code atual exibido pelo professor.",
  TOKEN_EXPIRADO:
    "O QR Code expirou. Leia o novo código exibido pelo professor.",
  FORA_DO_RAIO:
    "Você está fora do raio da sala. Aproxime-se e confira se a localização do aparelho está correta.",
  ALUNO_NAO_MATRICULADO:
    "Você não está matriculado nesta turma. Procure o professor.",
  PRESENCA_DUPLICADA:
    "Sua presença já foi registrada nesta chamada. Não é necessário enviar novamente.",
  DISPOSITIVO_JA_UTILIZADO:
    "Este aparelho já registrou a presença de outro aluno nesta chamada. Use seu próprio aparelho ou procure o professor.",
  LIMITE_EXCEDIDO:
    "Muitas tentativas em pouco tempo. Aguarde alguns segundos e tente novamente.",
  ERRO_INTERNO:
    "O servidor não conseguiu confirmar a presença. Tente novamente em instantes.",
} satisfies Record<string, string>;

export function mensagemDoErro(erro: Erro): string {
  return Object.hasOwn(errosCheckin, erro.codigo)
    ? errosCheckin[erro.codigo as keyof typeof errosCheckin]
    : "Não foi possível confirmar a presença. Tente novamente ou procure o professor.";
}
