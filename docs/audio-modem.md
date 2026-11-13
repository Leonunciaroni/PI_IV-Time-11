# Modem de áudio FSK v1

[audio/modem.mjs](../audio/modem.mjs) implementa a codificação e decodificação do
token de seis dígitos da #26. O emissor da #46 e o receptor de microfone usam esse
codec sem depender das telas dos apps. Há uma demonstração para testar no navegador.

**Situação:** código e testes digitais disponíveis; transmissão física a 3 m ainda
pendente. A [#13](https://github.com/Leonunciaroni/PI_IV-Time-11/issues/13) continua
necessária para escolher a faixa e o volume finais. Os valores abaixo são um perfil
inicial configurável, sem comprovação de alcance ou audibilidade.

## Enquadramento no ar

O canal sonoro transmite apenas o token; `sessaoId` vem da chamada ativa consultada
pela API, conforme [protocolo v1, seção 6](protocolo-v1.md#6-conteúdo-transmitido-por-canal).
Todos os bytes saem com o **bit mais significativo primeiro**.

| Posição (bytes) | Conteúdo | Tamanho |
|---|---|---|
| 0–3 | Preâmbulo `55 55 55 55` hexadecimal, alternância `01010101` | 32 bits |
| 4–5 | Sincronismo `D3 91` hexadecimal | 16 bits |
| 6 | Versão `01` | 8 bits |
| 7–12 | Token em seis bytes ASCII, preservando zeros à esquerda | 48 bits |
| 13–14 | CRC-16 dos bytes 6–12, byte alto primeiro | 16 bits |

O CRC é **CRC-16/IBM-3740**, também chamado CCITT-FALSE: largura 16, polinômio
`0x1021`, inicialização `0xFFFF`, entrada e saída sem reflexão e XOR final zero.
O vetor ASCII `123456789` resulta em `0x29B1`. Cabeçalho, versão e formato do token
também são conferidos. Exemplo completo para `001234`, com CRC `BD44`:

```text
55 55 55 55 D3 91 01 30 30 31 32 33 34 BD 44
```

O checksum detecta corrupção do quadro. Autenticação e validade temporal continuam
sendo verificadas pelo servidor com JWT e TOTP.

## Perfil FSK configurável

| Parâmetro | Valor inicial | Regra |
|---|---|---|
| `frequencia0` | 18.500 Hz | Tom do bit 0; faixa permitida 18–20 kHz |
| `frequencia1` | 19.500 Hz | Tom do bit 1; faixa permitida 18–20 kHz |
| `duracaoSimboloMs` | 20 ms (50 bit/s) | De 10 a 100 ms; igual nos dois lados |
| `amplitudeMinima` | 0,0001 | Amplitude mínima estimada do tom na janela analisada |
| `contrasteMinimo` | 0,6 | Mínimo de `abs(P1-P0)/(P1+P0)` para decidir o bit |
| Taxa de amostragem | A do `AudioContext` | Nyquist deve superar o maior tom com margem de 100 Hz |

A separação entre os tons precisa ser ao menos `2000 / duracaoSimboloMs` Hz.
Um símbolo ocupa `round(taxaAmostragem * duracaoSimboloMs / 1000)` amostras.
O receptor usa sua própria taxa de captura; tons e duração dos símbolos precisam
coincidir nos dois aparelhos.

São **120 bits por quadro**: com o perfil inicial, 2,4 s de sinal, mais 60 ms de
silêncio antes e depois, totalizando **2,52 s**. As margens acomodam as rampas de
50 ms do emissor sem atenuar símbolos úteis. O sinal mantém fase contínua nas
trocas de tom e um envelope de 5 ms nas extremidades. O volume inicial é 30%,
conforme o procedimento do spike.

## Recepção e sincronização

O decodificador procura preâmbulo e sincronismo em oito fases por símbolo e
centraliza a fase entre as posições válidas. Usa Goertzel para comparar a energia
nos dois tons, sobre os 60% centrais de cada símbolo. Só quadros completos com
versão, token e CRC válidos produzem resultado.

`DecodificadorFsk.processar()` aceita blocos PCM mono `Float32Array` de qualquer
tamanho, incluindo fragmentos ou vários quadros juntos. O buffer circular tem
tamanho fixo de 128 símbolos (aproximadamente 480 KiB em 48 kHz / 20 ms).
Após corrupção ou interrupção, a busca continua no próximo preâmbulo.
Não há correção de erros ou retransmissão automática no codec.

[receptor.mjs](../audio/receptor.mjs) solicita microfone mono com cancelamento de
eco, supressão de ruído e ganho automático desligados. Um filtro passa-altas reduz
os graves. Um AudioWorklet envia lotes de 2.048 amostras para um Worker, onde ocorre
a decodificação. A saída do worklet é silenciosa, sem reproduzir o microfone nos
alto-falantes. As restrições de captura são pedidos ao navegador; as medições
precisam conferir a resposta real do aparelho, sistema e navegador.

```js
import { ReceptorFsk } from '../audio/receptor.mjs';

const receptor = new ReceptorFsk({
  configuracao: { frequencia0: 18500, frequencia1: 19500, duracaoSimboloMs: 20 },
  aoToken: (token) => mostrarToken(token),
  aoErro: (erro) => mostrarErro(erro.message),
});
await receptor.iniciar(); // dentro do clique do usuário
await receptor.parar();  // ao encerrar a captura ou desmontar a tela
```

A parada libera tracks, Worker e contexto; também descarta uma captura concedida
depois do cancelamento. O chamador deve parar o receptor ao sair da tela.
`aoToken` entrega o token com CRC válido. O app deve obter a sessão pela API e
enviar o check-in autenticado do protocolo. A integração às telas é uma etapa
separada e não foi ativada por este módulo.

## Testar no navegador

A demonstração não exige Docker, API ou login. Na raiz, com Python 3:

```sh
python -m http.server 8765 --directory audio --bind 127.0.0.1
```

Abra **http://localhost:8765/demo/**. No Linux/macOS, o executável pode se chamar
`python3`. Sirva a pasta `audio` inteira, para que os módulos e workers sejam
encontrados. Não abra o HTML por `file://`.

1. Execute **Autoteste digital**. O token deve voltar com CRC válido, sem reproduzir
   som nem abrir o microfone. Se falhar, o motivo aparece junto ao botão. Use
   **Restaurar configuração inicial** para retornar ao token `001234`, frequências
   18.500/19.500 Hz e símbolos de 20 ms e executar novamente.
2. Para o caminho acústico, abra a página no emissor e no receptor com o mesmo perfil.
   O microfone do celular exige HTTPS, por exemplo um túnel temporário para a porta 8765.
3. No receptor, toque em **Ouvir microfone** e permita o acesso. No notebook, toque
   em **Iniciar emissão**. O primeiro quadro pode esperar a próxima janela de 15 s;
   o token de teste permanece fixo durante essa execução.
4. Confira o token e o CRC. Ao terminar, pare emissão e escuta. Mantenha as páginas
   em primeiro plano: a demonstração encerra captura e emissão ao ocultar a aba.

Com `cloudflared` disponível, use `cloudflared tunnel --url http://localhost:8765`.
Abra no celular o endereço HTTPS dos logs acrescentando `/demo/`. O túnel do Compose
da #40 serve os apps; esta demonstração usa seu próprio servidor. O áudio é
processado localmente, sem gravação ou envio pelo túnel.

### Validação física pendente

Depois de escolher a faixa com a #13, execute o autoteste em cada navegador e faça
a recepção a **3 m em sala silenciosa**, com notebook e celular identificados.
Compare o token recebido com o emitido e registre perfil, volumes do sistema/página,
taxa de amostragem, sucessos e falhas. Testes digitais não comprovam o caminho físico.

| Data | Emissor / receptor / navegador | Distância | Perfil / volume | Token esperado / recebido | Resultado |
|---|---|---|---|---|---|
| Pendente | — | 3 m | — | — | A medir (#26) |

## Emissão em loop (#46)

O módulo reutilizável [audio/emissor.mjs](../audio/emissor.mjs) implementa o loop de
emissão da issue #46, sem depender das telas. Envia um quadro do token ao iniciar
e um novo quadro em cada fronteira TOTP. Ao iniciar perto da fronteira, aguarda
a próxima janela se o quadro completo não couber. Gerações atrasadas são descartadas.

O relógio deve ser o do servidor, corrigido pela diferença medida no WebSocket.
`periodoSegundos` e `t0` vêm da configuração TOTP da sessão. O agendamento usa
fronteiras absolutas, evitando acumular o atraso dos temporizadores do navegador.
Abas suspensas podem atrasar a emissão; ao retornar, o módulo recalcula a janela.

```js
import { EmissorToken } from "../audio/emissor.mjs";
import { criarModem } from "../audio/modem.mjs";

const modem = criarModem({ frequencia0: 18500, frequencia1: 19500, duracaoSimboloMs: 20 });

const emissor = new EmissorToken({
  contexto: new AudioContext(),
  periodoSegundos: config.periodoSegundos,
  t0: config.t0,
  agora: () => Date.now() + cliente.diferencaRelogioMs,
  gerarToken: (instante) => codigoTotp(parametros, instante),
  codificar: (token, contexto) => modem.codificar(token, contexto),
  aoErro: (erro) => mostrarErro(erro.message),
});
// Dentro do clique de iniciar, para liberar o áudio no navegador:
await emissor.iniciar();
// Ao encerrar a chamada ou desmontar a tela:
emissor.parar();
```

`codificar` é o ponto de integração com o modem FSK e deve devolver um `AudioBuffer`
mono com o quadro completo (preâmbulo, token e verificação), como o adaptador do
modem acima. Ele já reserva 60 ms em cada ponta para as rampas não atenuarem os
símbolos úteis. A duração precisa superar 100 ms e caber
na janela TOTP. O contexto pertence ao chamador, que pode fechá-lo após o fade de
50 ms ao terminar de usar o áudio.

Cada quadro começa e termina com ganho zero, usando rampas lineares de 50 ms.
A parada mantém o ganho instantâneo antes de reduzi-lo a zero, inclusive durante
uma rampa. Fontes e ganhos são desconectados ao terminar. O volume padrão é 0,3,
os 30% usados no [procedimento do spike](spikes/audio-ultrassom.md). As medições
físicas continuam pendentes: as rampas evitam descontinuidades, mas a audibilidade
e o alcance precisam ser verificados no hardware da sala.

Verificação automatizada, sem dependências:

```sh
node --test audio/*.test.mjs
```

Os testes cobrem CRC conhecido, um quadro de referência independente, alteração
de cada bit, zeros à esquerda, configurações inválidas, 44,1/48/96 kHz, blocos
irregulares, fase inicial, ruído, eco e pequena diferença de relógio sintéticos,
corrupção, ressincronização e memória limitada, além do controlador da #46.

No Chrome, foi exercitado o caminho WAV como microfone simulado → filtro →
AudioWorklet → Worker → token. Também foram conferidos parada, aba oculta,
permissão negada, cancelamento tardio, contexto inseguro e `AudioBuffer` renderizado
em `OfflineAudioContext`. A saída física foi silenciada nessa verificação.

## Referências técnicas

- [Web Audio / AudioWorklet (W3C)](https://www.w3.org/TR/webaudio/#AudioWorklet).
- [Media Capture and Streams (W3C)](https://www.w3.org/TR/mediacapture-streams/).
- [Goertzel (Texas Instruments, SPRA066)](https://www.ti.com/lit/an/spra066/spra066.pdf).
- [CRC-16/IBM-3740 (CRC RevEng)](https://reveng.sourceforge.io/crc-catalogue/16.htm#crc.cat.crc-16-ibm-3740).
- [Quick Tunnels (Cloudflare)](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).
