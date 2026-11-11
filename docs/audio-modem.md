# Emissão do token sonoro

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
mono com o quadro completo (preâmbulo, token e verificação). O codec FSK e seu
enquadramento ainda não estão implementados neste checkout; este módulo não define
um formato alternativo. Reserve 50 ms de margem em cada ponta do quadro para as
rampas não atenuarem os símbolos úteis. A duração precisa superar 100 ms e caber
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
node --test audio/emissor.test.mjs
```
