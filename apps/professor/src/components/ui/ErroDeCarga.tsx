/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import { Botao } from "./Botao";

/** Falha ao carregar dados, com a mensagem da API e a opção de tentar de novo. */
export function ErroDeCarga({ mensagem, aoTentarNovamente }: { mensagem: string; aoTentarNovamente: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-controle border border-perigo-borda bg-perigo-fundo p-4">
      <p role="alert" className="text-sm text-perigo-texto">
        {mensagem}
      </p>
      <Botao onClick={aoTentarNovamente}>Tentar novamente</Botao>
    </div>
  );
}
