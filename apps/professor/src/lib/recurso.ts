import { useCallback, useEffect, useState } from "react";
import { ErroApi, obter } from "./api";

export type Recurso<T> =
  | { situacao: "carregando" }
  | { situacao: "erro"; erro: ErroApi; recarregar: () => void }
  | { situacao: "pronto"; dados: T; recarregar: () => void };

interface Resultado<T> {
  chave: string;
  dados?: T;
  erro?: ErroApi;
}

/**
 * Carrega um recurso da API (GET autenticado) e refaz a chamada quando o caminho muda ou quando
 * `recarregar` é chamado. A chave identifica a requisição a que o resultado pertence, então um
 * resultado antigo nunca é mostrado para um caminho novo.
 */
export function useRecurso<T>(caminho: string): Recurso<T> {
  const [tentativa, setTentativa] = useState(0);
  const [resultado, setResultado] = useState<Resultado<T> | null>(null);
  const chave = `${caminho}#${tentativa}`;

  useEffect(() => {
    let cancelado = false;
    obter<T>(caminho).then(
      (dados) => {
        if (!cancelado) setResultado({ chave, dados });
      },
      (e: unknown) => {
        if (cancelado) return;
        setResultado({ chave, erro: e instanceof ErroApi ? e : new ErroApi("Erro inesperado. Tente novamente.", 0) });
      },
    );
    return () => {
      cancelado = true;
    };
  }, [caminho, chave]);

  const recarregar = useCallback(() => setTentativa((n) => n + 1), []);

  if (resultado?.chave !== chave) return { situacao: "carregando" };
  if (resultado.erro) return { situacao: "erro", erro: resultado.erro, recarregar };
  return { situacao: "pronto", dados: resultado.dados as T, recarregar };
}
