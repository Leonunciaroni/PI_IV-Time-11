// Acompanhamento de uma sessão aberta (sessao.acompanhar, seção 4.5), sem React, para dar para
// testar com um cliente simulado. O hook useAcompanharSessao só liga isto à tela.

import {
  juntarPresencasAoVivo,
  listarPresentes,
  presencasAoVivoVazias,
  totaisAoVivo,
  type EntradaAoVivo,
  type PresenteAoVivo,
} from "./presencas";
import type { ModoChamada, PresencaAtualizada, SessaoEstado, SessaoModoAlterado } from "./protocolo";
import { ErroSessao, type ClienteSessao, type EstadoConexao } from "./ws";

/** O pedaço do ClienteSessao que o acompanhamento usa; nos testes, um cliente simulado. */
export type ClienteAcompanhamento = Pick<ClienteSessao, "estado" | "aoMudarEstado" | "aoEvento" | "requisitar">;

export interface SituacaoAoVivo {
  /** Do registro mais recente para o mais antigo. */
  presentes: PresenteAoVivo[];
  totalPresentes: number;
  /** null até chegar o primeiro estado ou evento. */
  totalMatriculados: number | null;
  /** Já chegou o estado do servidor ao menos uma vez. */
  carregado: boolean;
  /** Mensagem quando o servidor responde que a sessão foi encerrada ou não existe. */
  encerrada: string | null;
  erro: string | null;
  /** Modo segundo o servidor (estado e sessao.modo_alterado); null até chegar o primeiro. */
  modo: ModoChamada | null;
  /**
   * Sala segundo a última troca de modo recebida; undefined quando não houve troca nesta tela (o
   * sessao.estado não traz a sala), null quando a sessão ficou sem sala.
   */
  salaId: string | null | undefined;
}

const SESSAO_ACABOU = new Set(["SESSAO_ENCERRADA", "SESSAO_NAO_ENCONTRADA"]);

export class AcompanhamentoSessao {
  private presencas = presencasAoVivoVazias;
  private encerrada: string | null = null;
  private erro: string | null = null;
  /** Muda a cada pedido e a cada queda: resposta de pedido antigo ou de conexão que caiu é ignorada. */
  private geracao = 0;
  private modo: ModoChamada | null = null;
  private salaId: string | null | undefined = undefined;
  /**
   * Troca de modo recebida enquanto um sessao.acompanhar espera resposta. O servidor inscreve antes
   * de ler o estado, então a troca pode chegar antes de um estado que ainda traz o modo antigo: ela é
   * reaplicada sobre o estado, como os eventos de presença (juntarPresencasAoVivo).
   */
  private trocaDesdeOPedido: SessaoModoAlterado | null = null;
  private pedidoPendente = false;
  private foto: SituacaoAoVivo;
  private readonly ouvintes = new Set<() => void>();

  constructor(
    private readonly cliente: ClienteAcompanhamento,
    readonly sessaoId: string,
  ) {
    this.foto = this.montar();
  }

  /** Sempre o mesmo objeto enquanto nada muda, como pede o useSyncExternalStore. */
  get situacao(): SituacaoAoVivo {
    return this.foto;
  }

  inscrever = (ouvinte: () => void): (() => void) => {
    this.ouvintes.add(ouvinte);
    return () => {
      this.ouvintes.delete(ouvinte);
    };
  };

  /** Começa a escutar e pede o estado; devolve a função que para. Pode ser chamado de novo depois. */
  iniciar(): () => void {
    // Escuta os eventos antes do pedido: um deles pode chegar antes da resposta sessao.estado
    const pararEventos = this.cliente.aoEvento<PresencaAtualizada>("presenca.atualizada", (presenca) =>
      this.registrar(presenca),
    );
    const pararTrocas = this.cliente.aoEvento<SessaoModoAlterado>("sessao.modo_alterado", (troca) =>
      this.registrarTroca(troca),
    );
    const pararConexao = this.cliente.aoMudarEstado((estado) => this.aoMudarConexao(estado));
    if (this.cliente.estado === "pronto") void this.acompanhar();
    return () => {
      this.geracao++;
      pararEventos();
      pararTrocas();
      pararConexao();
    };
  }

  /** Evento presenca.atualizada, ou a resposta de presenca.marcar, que tem os mesmos campos. */
  registrar(presenca: PresencaAtualizada): void {
    if (presenca.sessaoId !== this.sessaoId) return;
    this.juntar({ tipo: "evento", evento: presenca });
  }

  /** Evento sessao.modo_alterado, ou a resposta de sessao.trocar_modo, que tem os mesmos campos. */
  registrarTroca(troca: SessaoModoAlterado): void {
    if (troca.sessaoId !== this.sessaoId) return;
    this.modo = troca.modo;
    this.salaId = troca.salaId;
    if (this.pedidoPendente) this.trocaDesdeOPedido = troca;
    this.notificar();
  }

  /** Pede o estado de novo (ex.: depois de um lote); resolve quando ele chega ou o pedido falha. */
  recarregar(): Promise<void> {
    return this.cliente.estado === "pronto" ? this.acompanhar() : Promise.resolve();
  }

  private aoMudarConexao(estado: EstadoConexao): void {
    // Na reconexão o servidor esqueceu a inscrição: é preciso pedir de novo (seção 4.5)
    if (estado === "pronto") void this.acompanhar();
    else this.geracao++;
  }

  private async acompanhar(): Promise<void> {
    const geracao = ++this.geracao;
    this.pedidoPendente = true;
    this.trocaDesdeOPedido = null;
    this.juntar({ tipo: "pedido" });
    try {
      const estado = await this.cliente.requisitar<SessaoEstado>("sessao.acompanhar", { sessaoId: this.sessaoId });
      if (geracao !== this.geracao || estado.sessaoId !== this.sessaoId) return;
      this.erro = null;
      // Uma troca chegada durante o pedido é mais nova que o modo lido pelo servidor, ou igual a ele.
      // O campo pode ter mudado durante o await (registrarTroca), por isso a leitura explícita.
      const trocaRecebida = this.trocaDesdeOPedido as SessaoModoAlterado | null;
      this.modo = trocaRecebida?.modo ?? estado.modo;
      this.pedidoPendente = false;
      this.trocaDesdeOPedido = null;
      this.juntar({ tipo: "estado", estado });
    } catch (e) {
      if (geracao !== this.geracao) return;
      this.pedidoPendente = false;
      this.trocaDesdeOPedido = null;
      if (e instanceof ErroSessao && SESSAO_ACABOU.has(e.codigo)) this.encerrada = e.message;
      else this.erro = e instanceof ErroSessao ? e.message : "Erro inesperado. Tente novamente.";
      this.notificar();
    }
  }

  private juntar(entrada: EntradaAoVivo): void {
    this.presencas = juntarPresencasAoVivo(this.presencas, entrada);
    // O pedido só abre a janela de eventos; a tela não muda
    if (entrada.tipo !== "pedido") this.notificar();
  }

  private notificar(): void {
    this.foto = this.montar();
    this.ouvintes.forEach((ouvinte) => ouvinte());
  }

  private montar(): SituacaoAoVivo {
    const totais = totaisAoVivo(this.presencas);
    return {
      presentes: listarPresentes(this.presencas),
      totalPresentes: totais.presentes,
      totalMatriculados: totais.matriculados,
      carregado: this.presencas.carregado,
      encerrada: this.encerrada,
      erro: this.erro,
      modo: this.modo,
      salaId: this.salaId,
    };
  }
}
