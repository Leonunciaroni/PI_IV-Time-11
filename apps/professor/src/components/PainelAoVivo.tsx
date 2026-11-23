/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
import type { ReactNode } from "react";
import type { PresenteAoVivo } from "@/lib/presencas";
import type { OrigemPresenca } from "@/lib/protocolo";

const hora = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });

const NOME_DA_ORIGEM: Record<OrigemPresenca, string> = { QRCODE: "QR", SOM: "Som", MANUAL: "Manual" };

interface PainelAoVivoProps {
  /** Presentes segundo o servidor (estado e eventos), do mais recente para o mais antigo. */
  presentes: PresenteAoVivo[];
  /** O estado da sessão já chegou do servidor. */
  carregado: boolean;
  /** Texto da lista vazia, que muda com o modo (código ou som). */
  vazio: string;
  /** Ações abaixo da lista, como as trocas de modo. */
  children?: ReactNode;
}

/** "Presentes (ao vivo)" das chamadas por QR Code (P7) e por som (P8). */
export function PainelAoVivo({ presentes, carregado, vazio, children }: PainelAoVivoProps) {
  return (
    <aside aria-labelledby="titulo-presentes" className="flex w-full flex-col gap-3 lg:w-80">
      <h2 id="titulo-presentes" className="flex items-center gap-2 font-semibold">
        <span aria-hidden="true" className="size-2 rounded-full bg-sucesso" />
        Presentes (ao vivo)
      </h2>
      {presentes.length === 0 ? (
        <p role="status" className="text-sm text-suave">
          {carregado ? vazio : "Carregando os presentes..."}
        </p>
      ) : (
        <ol className="border-t border-borda-suave">
          {presentes.map((presenca) => (
            <li key={presenca.alunoId} className="flex items-baseline gap-3 border-b border-borda-suave py-2">
              <span className="font-mono text-sm text-suave">{hora.format(presenca.registradoEm)}</span>
              <span className="min-w-0 flex-1">{presenca.nome}</span>
              <span className="text-xs text-suave">{NOME_DA_ORIGEM[presenca.origem]}</span>
            </li>
          ))}
        </ol>
      )}
      {children}
    </aside>
  );
}
