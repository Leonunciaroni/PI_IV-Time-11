import { useMemo, useRef, useState } from "react";
import { contarPresentes, marcacoesExibidas, soltarPendentes, type Pendentes } from "./presencas";
import type { PresencaAtualizada, PresencaLoteOk } from "./protocolo";
import { useRecurso } from "./recurso";
import type { Aluno } from "./tipos";
import type { AoVivo } from "./useAcompanharSessao";
import { ErroSessao, type ClienteSessao } from "./ws";

/**
 * Estado da chamada clássica (P6). Quem está presente vem do servidor (sessao.estado e eventos,
 * inclusive de outras abas); cada toque muda a lista na hora e é desfeito se o servidor recusar.
 */
export function useChamadaClassica(
  sessaoId: string,
  turmaId: string,
  cliente: ClienteSessao,
  aoVivo: AoVivo,
  /** Chamado quando o servidor responde que a sessão já foi encerrada (pelo professor ou por tempo). */
  aoEncerrarNoServidor: (mensagem: string) => void,
) {
  const alunos = useRecurso<Aluno[]>(`/api/turmas/${turmaId}/alunos`);
  // Toques sem resposta, por cima do que o servidor diz
  const [pendentes, setPendentes] = useState<Pendentes>({});
  const ultimoToque = useRef(0);
  const [emLote, setEmLote] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const marcacoes = useMemo(() => marcacoesExibidas(aoVivo.presentes, pendentes), [aoVivo.presentes, pendentes]);
  const lista = alunos.situacao === "pronto" ? alunos.dados : [];

  function falhou(e: unknown) {
    if (e instanceof ErroSessao && e.codigo === "SESSAO_ENCERRADA") aoEncerrarNoServidor(e.message);
    else setErro(e instanceof ErroSessao ? e.message : "Erro inesperado. Tente novamente.");
  }

  async function alternar(alunoId: string) {
    const presente = marcacoes[alunoId] !== true;
    const id = ++ultimoToque.current;
    setErro(null);
    setPendentes((atuais) => ({ ...atuais, [alunoId]: { presente, id } }));
    try {
      const resposta = await cliente.requisitar<PresencaAtualizada>("presenca.marcar", { sessaoId, alunoId, presente });
      // A resposta entra na lista antes de soltar o toque, para não piscar o valor antigo enquanto
      // o evento não chega; quando ele chegar, a junção por alunoId o ignora
      aoVivo.registrar(resposta);
    } catch (e) {
      falhou(e);
    } finally {
      // Recusado: sem o toque por cima, a lista volta ao que o servidor diz
      setPendentes((atuais) => soltarPendentes(atuais, id));
    }
  }

  /** "Marcar todos presentes" (true) ou "Limpar" (false); depois o professor desmarca as exceções. */
  async function marcarLote(presente: boolean) {
    const id = ++ultimoToque.current;
    setErro(null);
    setEmLote(true);
    setPendentes(Object.fromEntries(lista.map((aluno) => [aluno.id, { presente, id }])));
    try {
      await cliente.requisitar<PresencaLoteOk>("presenca.marcar_lote", { sessaoId, presente });
      // O lote responde só os totais; o estado novo diz quem ficou presente
      await aoVivo.recarregar();
    } catch (e) {
      falhou(e);
    } finally {
      setPendentes((atuais) => soltarPendentes(atuais, id));
      setEmLote(false);
    }
  }

  return {
    alunos,
    marcacoes,
    presentes: contarPresentes(lista, marcacoes),
    total: lista.length,
    emLote,
    erro,
    alternar,
    marcarLote,
  };
}
