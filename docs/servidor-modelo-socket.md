# Servidor de sessão no modelo cliente-servidor com sockets

O transporte do `sessao/` segue o material "Fazedor de Continhas" (pastas `Servidor/` e `Cliente/`):
threads de aceitação e de supervisão por conexão, `ObjectOutputStream` criado antes do
`ObjectInputStream`, a classe `Parceiro` e mensagens `Comunicado` serializadas. As regras de negócio
(`sessao/chamada/*`, `sessao/totp/*`) não mudam.

Esta etapa cobre só o transporte TCP entre o servidor e clientes Java. A ponte WebSocket↔TCP para os
apps web é a etapa 2, e `docs/protocolo-v1.md` continua descrevendo o que os apps veem.

## 1. Mapeamento modelo → projeto

| Modelo (`Servidor/`) | Projeto (`sessao/`, salvo indicação) | Observação |
|---|---|---|
| `Servidor` (main, `PORTA_PADRAO`, `[PORTA]`, `desativar`) | `sessao/Servidor` | `PORTA_PADRAO = "3000"` (como no modelo; o Compose passa 8081 por argumento). Console `desativar` envia `ComunicadoDeDesligamento` a todos, chama `System.exit(0)`. |
| `Teclado` | `sessao/Teclado` | Só `getUmString()`. |
| `AceitadoraDeConexao` | `sessao/servidor/AceitadoraDeConexao` | Igual ao modelo; recebe também `Servicos`. |
| `SupervisoraDeConexao` | `sessao/servidor/SupervisoraDeConexao` | Um thread por conexão; despacho por `instanceof`. Absorve a lógica de `RoteadorDeMensagens` (ver seção 4). |
| `Parceiro` | `sessao/servidor/Parceiro` | Mesmos métodos e semáforo. Desvios na seção 3. |
| `Comunicado`, `ComunicadoDeDesligamento`, `PedidoDeOperacao`, `Resultado`, `PedidoParaSair` | `common/comunicado/*` | Pacote compartilhado com clientes futuros. Ver seção 2. |
| `Cliente/TratadoraDeComunicadoDeDesligamento` | Não há no servidor | Fica para o cliente Java de teste, se for preciso. |
| Não existe no modelo | `sessao/servidor/Servicos` | Monta os serviços, o MongoDB, o observador de change streams e o agendador do tempo limite. |
| Não existe no modelo | `sessao/servidor/FiltroDeComunicados` | Allowlist de desserialização (seção 3). |
| Não existe no modelo | `sessao/servidor/LimitadorDeMensagens` | Mantido: limite de 60 mensagens em 10 s. |

Removidos do módulo: `servidor/ServidorSessao` (WebSocket), `servidor/Conexao`,
`servidor/ProcessadorDeMensagens`, `protocolo/RoteadorDeMensagens`, `SessaoApplication`, e a
dependência `Java-WebSocket` do `sessao/pom.xml` e do `pom.xml` raiz.

## 2. Comunicados

Todo comunicado é uma subclasse de `Comunicado` (`implements Serializable, Cloneable`), com campos
privados, getters e `toString`. Os tipos de valor `PresenteResumo` e `ConfiguracaoTotp` também ficam
no pacote, mas não são comunicados, são campos deles.

Pedidos (cliente → servidor). Cada um, exceto `PedidoParaSair`, espera exatamente uma resposta:

| Classe | Tipo no protocolo v1 | Resposta de sucesso |
|---|---|---|
| `PedidoDeAutenticacao` | `auth` | `RespostaDeAutenticacao` (`auth.ok`) |
| `PedidoDePing` | `ping` | `RespostaDePong` (`pong`) |
| `PedidoDeAbrirSessao` | `sessao.abrir` | `RespostaDeAbrirSessao` (`sessao.aberta`) |
| `PedidoDeEncerrarSessao` | `sessao.encerrar` | `RespostaDeEncerrarSessao` (`sessao.encerrada`) |
| `PedidoDeAcompanharSessao` | `sessao.acompanhar` | `RespostaDeAcompanharSessao` (`sessao.estado`) |
| `PedidoDeMarcarPresenca` | `presenca.marcar` | `RespostaDeMarcarPresenca` (`presenca.atualizada`) |
| `PedidoDeMarcarPresencaEmLote` | `presenca.marcar_lote` | `RespostaDeMarcarPresencaEmLote` (`presenca.lote_ok`) |
| `PedidoDeCheckin` | `checkin` | `RespostaDeCheckin` (`checkin.ok`) |
| `PedidoParaSair` | (conexão, sem tipo no protocolo) | nenhuma: o servidor remove o parceiro e fecha |

Respostas e eventos são classes diferentes, mesmo quando têm os mesmos campos:

| Classe | Tipo no protocolo | Quando é enviada |
|---|---|---|
| `Erro` | `erro` | Resposta a qualquer pedido que falhou (campo `codigo`: `CodigoErro`). |
| `RespostaDeAutenticacao`, `RespostaDePong`, `RespostaDeAbrirSessao`, `RespostaDeEncerrarSessao`, `RespostaDeAcompanharSessao`, `RespostaDeMarcarPresenca`, `RespostaDeMarcarPresencaEmLote`, `RespostaDeCheckin` | `auth.ok`, `pong`, `sessao.aberta`, `sessao.encerrada`, `sessao.estado`, `presenca.atualizada`, `presenca.lote_ok`, `checkin.ok` | Resposta ao pedido correspondente. |
| `EventoDePresencaAtualizada` | `presenca.atualizada` (sem `ref`) | Push aos acompanhantes da sessão. Não solicitado. |
| `EventoDeSessaoEncerrada` | `sessao.encerrada` (sem `ref`) | Push aos acompanhantes da sessão. Não solicitado. |
| `ComunicadoDeDesligamento` | (sem tipo: desligamento do servidor) | Enviado a todas as conexões pelo console `desativar`. Não solicitado. |

### Regra de correlação: uma resposta por pedido

- O servidor processa os pedidos de uma conexão em ordem, um de cada vez.
- Para cada pedido que espera resposta, o servidor envia **exatamente uma** mensagem: a resposta de
  sucesso ou um `Erro`. Isso vale também para `LIMITE_EXCEDIDO` e `ERRO_INTERNO`.
- Eventos (`EventoDe...`) e `ComunicadoDeDesligamento` são não solicitados e nunca contam como resposta.
- Por isso o transporte não tem `id` nem `ref`: a correlação é pela ordem. A ponte deve manter a
  fila de pedidos pendentes e atribuir `ref` na ordem de chegada.

### Lista das classes em `common/comunicado`

`Comunicado`, `ComunicadoDeDesligamento`, `PedidoParaSair`, `PedidoDeAutenticacao`, `PedidoDePing`,
`PedidoDeAbrirSessao`, `PedidoDeEncerrarSessao`, `PedidoDeAcompanharSessao`,
`PedidoDeMarcarPresenca`, `PedidoDeMarcarPresencaEmLote`, `PedidoDeCheckin`,
`RespostaDeAutenticacao`, `RespostaDePong`, `RespostaDeAbrirSessao`, `RespostaDeEncerrarSessao`,
`RespostaDeAcompanharSessao`, `RespostaDeMarcarPresenca`, `RespostaDeMarcarPresencaEmLote`,
`RespostaDeCheckin`, `Erro`, `EventoDePresencaAtualizada`, `EventoDeSessaoEncerrada`, e os valores
`PresenteResumo` e `ConfiguracaoTotp`.

Os records de `common/protocolo/dados` não foram apagados. Eles definem os campos JSON de cada tipo e
são usados por `TipoMensagem` e pelo teste `ExemplosDoProtocoloTest`, que confere os exemplos de
`docs/protocolo-v1.md`. Os nomes dos campos de cada comunicado são os mesmos dos componentes do record
correspondente.

## 3. Desvios do modelo

1. **Guard de stdin** (`Servidor`). Se `Teclado.getUmString()` devolver `null` (stdin fechado, como no
   contêiner sem `-i`), o servidor não lança `NullPointerException`: continua vivo, dormindo em laço,
   e continua atendendo a porta. O modelo quebraria nesse caso.
2. **`ObjectInputFilter`** (`FiltroDeComunicados`). A desserialização é de dados não confiáveis, e a
   serialização Java executa código de classes que estão no classpath. Por isso só são aceitos:
   classes do pacote `common/comunicado` (comunicados e os dois valores), enums de `ModoChamada`,
   `Papel`, `OrigemPresenca`, `CodigoErro` e `MotivoEncerramento`, `String`, `Boolean`, `Integer`,
   `Long`, `Double`, `Number` e `Enum`, que aparecem como superclasses desses tipos no fluxo,
   `ArrayList`, o array `Object[]` que o `ArrayList` usa, e os primitivos. O filtro
   também limita profundidade, número de referências e tamanho de arrays. Um objeto fora da allowlist
   fecha a conexão sem `Erro`, porque não há pedido identificável.
3. **`Parceiro.receba` sincronizado e com `reset()`**. No modelo só a supervisora escreve no parceiro.
   Aqui os eventos do `PainelAoVivo` e o desligamento escrevem em threads diferentes, então a escrita
   precisa ser sincronizada para não embaralhar o fluxo. O `reset()` após cada escrita evita que o
   `ObjectOutputStream` e o `ObjectInputStream` acumulem todos os objetos enviados numa sessão de até
   4 horas.
4. **Semáforo liberado em `finally`** no `espie`. O modelo deixava o semáforo travado se a leitura
   falhasse.
5. **Remoção da lista e do painel ao cair a conexão.** O modelo remove o parceiro só no
   `PedidoParaSair`. A supervisora remove em `finally` (em qualquer saída) e tira o parceiro dos
   acompanhantes do `PainelAoVivo`. Sem isso, o painel enviaria eventos a conexões mortas, e a regra
   de `docs/protocolo-v1.md` seção 4.5 (acompanhante que desconecta é retirado) deixaria de valer.
6. **`AceitadoraDeConexao` encerra** quando o `ServerSocket` é fechado (usado nos testes). O modelo
   ficaria girando em `accept()` falhando.
7. **Prazos pelo socket, sem varredura.** O prazo de autenticação e o timeout de inatividade são
   `Socket.setSoTimeout`: antes do `auth`, vale o prazo de 5 s, e depois, o de 60 s. O fim do token é um
   único agendamento por conexão, no agendador compartilhado, que fecha o socket (4003 no protocolo).
8. **`Json` continua em `sessao/protocolo`.** Não é transporte, e a ponte da etapa 2 precisa dele
   para converter JSON↔comunicado. Não foi movido para `common` porque `common` só tem as anotações
   do Jackson, por decisão do pom.

## 4. Regras que saíram do `RoteadorDeMensagens`

A supervisora aplica, nesta ordem, as mesmas regras de antes:

1. Limite de mensagens (`LimitadorDeMensagens`): acima de 60 em 10 s, `LIMITE_EXCEDIDO` como resposta
   ao pedido. Após 5 recusas seguidas, a conexão é fechada. `PedidoParaSair` não conta no limite.
2. Tipo de comunicado que o cliente não pode enviar (respostas, eventos, desligamento): `MENSAGEM_INVALIDA`.
3. `auth` repetido: `MENSAGEM_INVALIDA`. `auth` com JWT inválido ou expirado: `AUTH_INVALIDA`, e
   depois a conexão fecha. Token válido: `RespostaDeAutenticacao`, e o token expira na conexão com
   fechamento (4003 no protocolo).
4. Pedido de negócio antes de `auth`: `AUTH_REQUERIDA`. `ping` é aceito antes de `auth`.
5. Pedido de negócio: a validação e as regras ficam em `ServicoDeSessoes`, `ServicoDeChamadaClassica`,
   `ServicoDeCheckin` e `PainelAoVivo`, na ordem da seção de cada operação de `docs/protocolo-v1.md`.
   `ErroDeProtocolo` vira `Erro` com o `CodigoErro`. Exceção inesperada vira `Erro(ERRO_INTERNO)`
   como resposta ao pedido, e a conexão continua.

Exigir `presente` booleano (`MENSAGEM_INVALIDA`) não é mais verificação do servidor: os comunicados
são tipados, e a ponte da etapa 2 precisa recusar o JSON inválido antes de montar o comunicado.

## 5. O que a ponte (etapa 2) precisa saber

- Um frame JSON de `docs/protocolo-v1.md` vira um comunicado. Os campos são os do record de
  `common/protocolo/dados` com o mesmo tipo. Os comunicados de resposta e de evento trazem o `tipo`
  pelo nome da classe, e não pelo campo `tipo`.
- O envelope (`v`, `id`, `ref`, `ts`) é da ponte. O transporte não tem esses campos. A ponte gera o
  `ref` pela ordem dos pedidos pendentes (seção 2).
- `Envelope` continua em `common/protocolo`, sem uso no transporte.
- Fechamentos: o TCP não tem código de fechamento. A ponte deduz o código do WebSocket a partir do que
  recebe: `Erro(AUTH_INVALIDA)` ou fim de conexão sem `auth` → 4001; `ComunicadoDeDesligamento` → 1001;
  `Erro(LIMITE_EXCEDIDO)` repetido → 4008. Token expirado (4003) e inatividade (1000) fecham o socket
  sem nenhuma mensagem, e a ponte não tem como distinguir os dois. Decidir isso é trabalho da etapa 2.
- Frames: o limite de 16 KiB e o de 64 mensagens pendentes são da ponte. No TCP, a supervisora atende
  um pedido por vez, e o limite de pendentes vira o próprio buffer do TCP.
