import { useEffect, useState } from "react";
import { guardarMarcacoes, lerMarcacoes } from "./chamadas";
import { contarPresentes, marcarTodos, type Marcacoes } from "./presencas";
import type { PresencaAtualizada, PresencaLoteOk } from "./protocolo";
import { useRecurso } from "./recurso";
import type { Aluno } from "./tipos";
import { ErroSessao, type ClienteSessao } from "./ws";

/**
 * Estado da chamada clássica (P6). Cada toque muda a lista na hora e é desfeito se o servidor
 * recusar; as marcações ficam guardadas no navegador para a lista voltar igual ao recarregar.
 */
export function useChamadaClassica(
  sessaoId: string,
  turmaId: string,
  cliente: ClienteSessao,
  /** Chamado quando o servidor responde que a sessão já foi encerrada (pelo professor ou por tempo). */
  aoEncerrarNoServidor: (mensagem: string) => void,
) {
  const alunos = useRecurso<Aluno[]>(`/api/turmas/${turmaId}/alunos`);
  const [marcacoes, setMarcacoes] = useState<Marcacoes>(() => lerMarcacoes(sessaoId));
  const [emLote, setEmLote] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    guardarMarcacoes(sessaoId, marcacoes);
  }, [sessaoId, marcacoes]);

  const lista = alunos.situacao === "pronto" ? alunos.dados : [];

  function falhou(e: unknown, desfazer: () => void) {
    desfazer();
    if (e instanceof ErroSessao && e.codigo === "SESSAO_ENCERRADA") aoEncerrarNoServidor(e.message);
    else setErro(e instanceof ErroSessao ? e.message : "Erro inesperado. Tente novamente.");
  }

  async function alternar(alunoId: string) {
    const presente = marcacoes[alunoId] !== true;
    setErro(null);
    setMarcacoes((atuais) => ({ ...atuais, [alunoId]: presente }));
    try {
      await cliente.requisitar<PresencaAtualizada>("presenca.marcar", { sessaoId, alunoId, presente });
    } catch (e) {
      falhou(e, () => setMarcacoes((atuais) => ({ ...atuais, [alunoId]: !presente })));
    }
  }

  /** "Marcar todos presentes" (true) ou "Limpar" (false); depois o professor desmarca as exceções. */
  async function marcarLote(presente: boolean) {
    const anteriores = marcacoes;
    setErro(null);
    setEmLote(true);
    setMarcacoes(marcarTodos(lista, presente));
    try {
      await cliente.requisitar<PresencaLoteOk>("presenca.marcar_lote", { sessaoId, presente });
    } catch (e) {
      falhou(e, () => setMarcacoes(anteriores));
    } finally {
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
