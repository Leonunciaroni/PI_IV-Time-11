# Modem de áudio FSK v1 — issue #26

O modem transmite somente o token TOTP de seis dígitos. A emissão fica na tela de
chamada do professor; o aluno recebe o quadro pelo microfone e envia o token pelo
check-in autenticado. A sessão, a matrícula e a validade temporal continuam sendo
validadas pelo servidor. O áudio não contém identificadores de sessão e não é
gravado nem enviado ao servidor.

## Perfil implementado

| Parâmetro | Valor |
| --- | --- |
| Modulação | FSK binária, mono |
| Bit `0` | 18.500 Hz |
| Bit `1` | 19.500 Hz |
| Taxa de amostragem | 48.000 Hz (o receptor usa a taxa do `AudioContext`) |
| Duração do símbolo | 20 ms |
| Quadro | 120 bits, 2,4 s de símbolos |
| Margens silenciosas | 60 ms no começo e no fim |
| Volume inicial do emissor | 30% |

Os 60 ms silenciosos preservam o quadro durante as rampas de fade de 50 ms do
emissor. O quadro completo dura 2,52 s no perfil de 48 kHz. A configuração deve
caber na janela TOTP da chamada. O volume de 30% é o padrão de software; ainda não
é uma referência acústica aprovada em sala.

## Enquadramento e checksum

Cada byte é transmitido do bit mais significativo para o menos significativo. Os
15 bytes do quadro são:

| Bytes | Conteúdo |
| --- | --- |
| 0–3 | Preâmbulo `55 55 55 55` |
| 4–5 | Sincronismo `D3 91` |
| 6 | Versão `01` |
| 7–12 | Seis dígitos ASCII do token, preservando zeros à esquerda |
| 13–14 | CRC-16/IBM-3740 (CCITT-FALSE), big-endian |

O CRC cobre os bytes 6–12: polinômio `0x1021`, valor inicial `0xFFFF`, sem reflexão
e sem XOR final. O decodificador rejeita quadro com preâmbulo, versão, conteúdo ou
CRC inválidos; somente um token íntegro é encaminhado ao check-in. Para o token
`001234`, o quadro é `55 55 55 55 D3 91 01 30 30 31 32 33 34 BD 44`.

## Implementação no navegador

- Professor: [`apps/professor/src/lib/audio/modem.ts`](../apps/professor/src/lib/audio/modem.ts)
  codifica o quadro e [`apps/professor/src/lib/useEmissorSom.ts`](../apps/professor/src/lib/useEmissorSom.ts)
  integra o modem ao emissor em loop e ao controle de volume.
- Aluno: [`apps/aluno/public/audio/modem.mjs`](../apps/aluno/public/audio/modem.mjs)
  decodifica os quadros; o receptor, AudioWorklet e Worker ficam em
  [`apps/aluno/public/audio/`](../apps/aluno/public/audio/).
- A tela de check-in inicia o microfone por gesto do usuário, encerra a captura ao
  receber o primeiro quadro válido e só mostra sucesso após a confirmação do
  servidor. Para os detalhes do fluxo, veja
  [`docs/checkin-som.md`](checkin-som.md).

O perfil precisa ser igual nos dois lados. Se as frequências ou a duração do
símbolo mudarem depois das medições da #13, atualizar o codificador do professor,
o decodificador publicado pelo aluno e os testes correspondentes no mesmo conjunto
de mudanças.

## Verificação digital

Os testes automatizados exercitam o quadro, o CRC, a codificação, a sincronização e
a decodificação sem depender de microfone ou alto-falante. Para executá-los:

```sh
cd apps/professor
npm ci
npm test

cd ../aluno
npm ci
npm test
```

Um teste digital aprovado confirma o protocolo e o tratamento do sinal gerado pelo
código; não mede a resposta do alto-falante, o microfone ou o alcance entre
aparelhos reais.

## Validação física e medições restantes

O critério de decodificar o token a 3 m em sala silenciosa foi confirmado em
aparelhos reais. O spike da #13 ainda precisa registrar medições em outras
distâncias, com ruído, em sala vizinha e no corredor antes de fixar uma faixa e um
volume de referência para a equipe. Portanto, o perfil acima é o perfil
implementado; uma única medição não aprova o alcance para todos os aparelhos.

### Resultado registrado em 22/11/2026

| Campo | Resultado |
| --- | --- |
| Emissor | Notebook |
| Receptor | Redmi Note 14, Chrome |
| Distância e ambiente | 3 m, sala silenciosa |
| Perfil do modem | Padrão: 18.500/19.500 Hz, símbolos de 20 ms |
| Resultado | Check-in confirmado pelo servidor; o receptor decodificou um quadro íntegro |
| Volume do sistema e da página | Não informado |

O resultado confirma o critério de 3 m da #26. Registre os volumes e continue as
medições abrangentes da #13 separadamente.

Para repetir o critério de 3 m, use a emissão e o check-in reais em HTTPS (ou
`localhost` no computador):

1. Coloque o notebook emissor e o celular receptor a 3 m, com o alto-falante
   voltado para o celular, em sala silenciosa.
2. Mantenha o perfil padrão nos dois lados e o volume do sistema operacional
   fixo. Para medir o maior alcance do aparelho nesta tentativa, deixe o controle
   da página do professor em 100% e registre esse valor; ele não define sozinho o
   volume de referência da equipe.
3. Inicie a chamada no modo Som, toque em **Iniciar som** no professor e em
   **Iniciar escuta** no aluno, permitindo o microfone.
4. Considere sucesso somente quando o aluno mostrar a presença confirmada pelo
   servidor e ela aparecer no painel do professor. Registre aparelhos, navegadores,
   distância, ruído, volumes e resultado; uma falha não deve ser descrita como
   checksum válido.

Se não houver decodificação, repita primeiro a uma distância menor para separar
falha de integração de limitação de alcance. Registre o resultado real e mantenha
o critério de 3 m pendente até obter confirmação nessa distância.
