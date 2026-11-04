import { Botao } from "./Botao";

/** Falha ao carregar dados, com a mensagem da API e a opção de tentar de novo. */
export function ErroDeCarga({ mensagem, aoTentarNovamente }: { mensagem: string; aoTentarNovamente: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3">
      <p role="alert" className="text-sm text-perigo">
        {mensagem}
      </p>
      <Botao onClick={aoTentarNovamente}>Tentar novamente</Botao>
    </div>
  );
}
