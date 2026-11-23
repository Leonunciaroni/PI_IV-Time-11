/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
"use client";

import { useState, type SubmitEvent } from "react";
import { ErroApi, enviar } from "@/lib/api";
import { ErroLocalizacao, obterLocalizacao } from "@/lib/localizacao";
import { useRecurso } from "@/lib/recurso";
import { lerSala, type DadosSala, type ErrosSala, type Sala } from "@/lib/sala";
import { AreaAutenticada } from "./AreaAutenticada";
import { Botao } from "./ui/Botao";
import { Campo } from "./ui/Campo";
import { Carregando } from "./ui/Carregando";
import { ErroDeCarga } from "./ui/ErroDeCarga";
import { MensagemDeErro } from "./ui/MensagemDeErro";

const VAZIO: DadosSala = { nome: "", latitude: "", longitude: "", raioMetros: "30" };

// Ordem dos campos na tela, para focar o primeiro com erro
const CAMPOS: (keyof DadosSala)[] = ["nome", "latitude", "longitude", "raioMetros"];

const numero = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 6 });

type Aviso = { tipo: "sucesso" | "erro"; texto: string } | null;

export function Salas() {
  const salas = useRecurso<Sala[]>("/api/salas");
  const [dados, setDados] = useState<DadosSala>(VAZIO);
  const [erros, setErros] = useState<ErrosSala>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [aviso, setAviso] = useState<Aviso>(null);
  const [enviando, setEnviando] = useState(false);
  const [localizando, setLocalizando] = useState(false);

  function alterar(campo: keyof DadosSala, valor: string) {
    setDados((atual) => ({ ...atual, [campo]: valor }));
    // Some com o erro do campo assim que o usuário volta a digitar nele
    setErros((atuais) => ({ ...atuais, [campo]: undefined }));
    setAviso(null);
  }

  async function usarLocalizacao() {
    setAviso(null);
    setLocalizando(true);
    try {
      const posicao = await obterLocalizacao();
      setDados((atual) => ({
        ...atual,
        latitude: posicao.latitude.toFixed(6),
        longitude: posicao.longitude.toFixed(6),
      }));
      setErros((atuais) => ({ ...atuais, latitude: undefined, longitude: undefined }));
      setAviso({ tipo: "sucesso", texto: `Localização preenchida (precisão de ${Math.round(posicao.precisaoMetros)} m).` });
    } catch (e) {
      setAviso({ tipo: "erro", texto: e instanceof ErroLocalizacao ? e.message : "Não foi possível obter a localização." });
    } finally {
      setLocalizando(false);
    }
  }

  async function cadastrar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErroGeral(null);
    setAviso(null);

    const leitura = lerSala(dados);
    if (!leitura.ok) {
      setErros(leitura.erros);
      // Leva o foco ao primeiro campo com erro, para quem usa teclado ou leitor de tela
      const primeiro = CAMPOS.find((campo) => leitura.erros[campo]);
      const elemento = primeiro && evento.currentTarget.elements.namedItem(primeiro);
      if (elemento instanceof HTMLInputElement) elemento.focus();
      return;
    }
    setErros({});

    setEnviando(true);
    try {
      const criada = await enviar<Sala>("/api/salas", leitura.sala);
      setDados(VAZIO);
      setAviso({ tipo: "sucesso", texto: `Sala “${criada.nome}” cadastrada.` });
      if (salas.situacao !== "carregando") salas.recarregar();
    } catch (e) {
      setErroGeral(e instanceof ErroApi ? e.message : "Erro inesperado. Tente novamente.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <AreaAutenticada>
      {() => (
        <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 p-6">
          <div>
            <h1 className="text-xl font-semibold">Salas</h1>
            <p className="text-sm text-suave">
              As salas valem para todos os professores e definem a distância aceita no modo QR Code.
            </p>
          </div>

          <div className="flex flex-col gap-8 md:flex-row md:items-start">
            <form onSubmit={cadastrar} noValidate className="flex flex-col gap-4 md:w-80 md:shrink-0">
              <h2 className="font-semibold">Nova sala</h2>

              <Campo
                rotulo="Nome"
                name="nome"
                placeholder="H15 - Sala 203"
                required
                autoComplete="off"
                value={dados.nome}
                onChange={(e) => alterar("nome", e.target.value)}
                erro={erros.nome}
              />

              <div className="grid grid-cols-2 gap-3">
                <Campo
                  rotulo="Latitude"
                  name="latitude"
                  inputMode="decimal"
                  placeholder="-22.8337"
                  required
                  autoComplete="off"
                  value={dados.latitude}
                  onChange={(e) => alterar("latitude", e.target.value)}
                  erro={erros.latitude}
                />
                <Campo
                  rotulo="Longitude"
                  name="longitude"
                  inputMode="decimal"
                  placeholder="-47.0525"
                  required
                  autoComplete="off"
                  value={dados.longitude}
                  onChange={(e) => alterar("longitude", e.target.value)}
                  erro={erros.longitude}
                />
              </div>

              <Botao variante="secundario" onClick={usarLocalizacao} disabled={localizando}>
                {localizando ? "Obtendo localização..." : "Usar minha localização"}
              </Botao>

              <Campo
                rotulo="Raio em metros"
                name="raioMetros"
                inputMode="decimal"
                required
                autoComplete="off"
                // A dica some enquanto há erro no campo, para não empilhar dois textos
                dica={erros.raioMetros ? undefined : "Distância máxima aceita entre o aluno e a sala no check-in."}
                value={dados.raioMetros}
                onChange={(e) => alterar("raioMetros", e.target.value)}
                erro={erros.raioMetros}
              />

              <Botao type="submit" disabled={enviando}>
                {enviando ? "Salvando..." : "Cadastrar sala"}
              </Botao>

              {aviso && (
                <p role="status" className={`text-sm ${aviso.tipo === "sucesso" ? "text-sucesso" : "text-perigo"}`}>
                  {aviso.texto}
                </p>
              )}
              <MensagemDeErro>{erroGeral}</MensagemDeErro>
            </form>

            <section aria-labelledby="titulo-salas" className="flex min-w-0 flex-1 flex-col gap-3">
              <h2 id="titulo-salas" className="font-semibold">
                Salas cadastradas
              </h2>
              {salas.situacao === "carregando" && <Carregando texto="Carregando salas..." />}
              {salas.situacao === "erro" && (
                <ErroDeCarga mensagem={salas.erro.message} aoTentarNovamente={salas.recarregar} />
              )}
              {salas.situacao === "pronto" &&
                (salas.dados.length === 0 ? (
                  <p className="text-sm text-suave">
                    Nenhuma sala cadastrada ainda. Use o formulário “Nova sala” para cadastrar a primeira.
                  </p>
                ) : (
                  // No celular latitude e longitude dividem uma coluna, para a tabela caber sem rolar de lado
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-borda-forte">
                        <th scope="col" className="py-2 pr-3 font-semibold">
                          Nome
                        </th>
                        <th scope="col" className="py-2 pr-3 font-semibold sm:hidden">
                          Coordenadas
                        </th>
                        <th scope="col" className="hidden py-2 pr-3 font-semibold sm:table-cell">
                          Latitude
                        </th>
                        <th scope="col" className="hidden py-2 pr-3 font-semibold sm:table-cell">
                          Longitude
                        </th>
                        <th scope="col" className="py-2 text-right font-semibold">
                          Raio
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {salas.dados.map((sala) => (
                        <tr key={sala.id} className="border-b border-borda-suave">
                          <td className="py-2 pr-3 break-words">{sala.nome}</td>
                          <td className="py-2 pr-3 font-mono text-suave sm:hidden">
                            <span className="block">{numero.format(sala.latitude)}</span>
                            <span className="block">{numero.format(sala.longitude)}</span>
                          </td>
                          <td className="hidden py-2 pr-3 font-mono text-suave sm:table-cell">
                            {numero.format(sala.latitude)}
                          </td>
                          <td className="hidden py-2 pr-3 font-mono text-suave sm:table-cell">
                            {numero.format(sala.longitude)}
                          </td>
                          <td className="py-2 text-right whitespace-nowrap">{numero.format(sala.raioMetros)} m</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ))}
            </section>
          </div>
        </main>
      )}
    </AreaAutenticada>
  );
}
