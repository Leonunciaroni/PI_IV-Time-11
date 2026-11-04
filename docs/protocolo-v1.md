# Protocolo de mensagens — versão 1

Este documento define o protocolo JSON trocado via WebSocket entre as aplicações (professor e
aluno) e o **servidor de sessão** (`sessao/`). Operações que não são de tempo real, como cadastro,
login e relatórios, usam a API REST (`api/`) e estão fora deste documento.

As classes Java correspondentes ficam em `common/src/main/java/.../common/protocolo`, e qualquer
mudança neste documento deve ser refletida nelas no mesmo pull request.

## 1. Transporte

| Item | Valor |
|---|---|
| URL | `ws(s)://<host>/ws` (o Nginx encaminha para o servidor de sessão) |
| Codificação | Texto UTF-8; cada frame contém **exatamente uma** mensagem JSON |
| Versão | `1`, informada no campo `v` de toda mensagem |
| Autenticação | JWT emitido pela API, enviado na mensagem `auth` (seção 4.1) |
| Keep-alive | O cliente envia `ping` a cada 25 s; o servidor fecha conexões sem tráfego por 60 s (fechamento 1000) |

O token **não** é enviado na URL, para não aparecer em logs do Nginx e do navegador.

Limites do servidor (valores fixos na implementação, não fazem parte da mensagem em si; o cliente
deve respeitá-los):

- Frames de até 16 KiB. Frames binários são recusados com `erro` `MENSAGEM_INVALIDA`, sem `ref`.
- Até 60 mensagens em qualquer janela de 10 s (`LimitadorDeMensagens`). As excedentes recebem
  `erro` `LIMITE_EXCEDIDO`, com `ref` quando o `id` pode ser lido. Após 5 recusas seguidas, a
  conexão é fechada com 4008.
- Até 64 mensagens pendentes de processamento por conexão. Acima disso, a conexão é fechada com 4008.

## 2. Envelope

Toda mensagem, em qualquer direção, segue o mesmo envelope:

```json
{
  "v": 1,
  "tipo": "ping",
  "id": "a1b2c3",
  "ref": null,
  "ts": 1791370800000,
  "dados": { }
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `v` | inteiro | sim | Versão do protocolo. O servidor rejeita versões diferentes de `1`. |
| `tipo` | string | sim | Tipo da mensagem (seções 4 e 5). |
| `id` | string | em requisições | Identificador gerado pelo remetente para correlação. Até 64 caracteres; mais que isso é `MENSAGEM_INVALIDA`, sem `ref`. |
| `ref` | string | em respostas | `id` da requisição que originou esta resposta. Ausente em eventos (push). |
| `ts` | inteiro | sim | Instante de envio em milissegundos desde a época Unix (UTC). |
| `dados` | objeto | sim | Corpo específico do tipo. Usa `{}` quando não há conteúdo. |

Regras gerais:

- Campos desconhecidos são ignorados, o que permite evolução compatível dentro da versão 1.
- Datas trafegam como milissegundos Unix (inteiro); identificadores, como strings (ObjectId do MongoDB em hexadecimal).
- Toda requisição recebe **uma** resposta: o tipo de sucesso indicado na tabela ou um `erro`, ambos com `ref` preenchido.
  Exceções: `erro` sem `ref` para frame binário e para falha interna (`ERRO_INTERNO`), que não
  permitem correlacionar a mensagem.
- `v` ausente ou não inteiro é `MENSAGEM_INVALIDA`; `v` inteiro diferente de `1` é
  `VERSAO_NAO_SUPORTADA`, seguido de fechamento 4002.

## 3. Ciclo de vida da conexão

```
cliente                                 servidor de sessão
   |---- abre WebSocket /ws ------------------>|
   |---- auth { token } ---------------------->|  (prazo: 5 s após abrir)
   |<--- auth.ok { usuarioId, papel } ---------|
   |---- ping -------------------------------->|
   |<--- pong ---------------------------------|
   |          ... requisições e eventos ...    |
   |<--- close (código 1000, 1001 ou 40xx) ----|
```

Antes de `auth.ok`, qualquer mensagem diferente de `auth` e `ping` é respondida com
`erro` `AUTH_REQUERIDA`. Se o `auth` não chegar em 5 s, a conexão é fechada com código `4001`.
Um `auth` com token inválido, expirado ou sem assinatura correta recebe `erro` `AUTH_INVALIDA` e é
fechado com `4001`. Um `auth` repetido em conexão já autenticada é `MENSAGEM_INVALIDA`.

O fechamento, em qualquer momento, usa os códigos da seção 8.

## 4. Mensagens cliente → servidor

| Tipo | Quem envia | Resposta de sucesso | Semana do cronograma |
|---|---|---|---|
| `auth` | ambos | `auth.ok` | 2 |
| `ping` | ambos | `pong` | 2 |
| `sessao.abrir` | professor | `sessao.aberta` | 3 |
| `sessao.encerrar` | professor | `sessao.encerrada` | 3 |
| `sessao.acompanhar` | professor | `sessao.estado` | 4 |
| `presenca.marcar` | professor | `presenca.atualizada` | 4 |
| `presenca.marcar_lote` | professor | `presenca.lote_ok` | 4 |
| `checkin` | aluno | `checkin.ok` | 5 (QR), 6 (som) |

Situação no servidor da branch `develop`: estão implementados todos os tipos desta tabela, com
`checkin` nos canais QRCODE e SOM. Os eventos da seção 5.2 são produzidos a partir dos Change Streams
do MongoDB, como descrito na seção 5.2.

### 4.1 `auth`

```json
{ "token": "<JWT emitido pela API>" }
```

### 4.2 `ping`

`dados` vazio. Também pode ser enviado antes da autenticação.

### 4.3 `sessao.abrir`

```json
{ "turmaId": "66f1c0a1b2c3d4e5f6a7b8c9", "modo": "QRCODE", "salaId": "66f1c1a1b2c3d4e5f6a7b8c9" }
```

| Campo | Descrição |
|---|---|
| `turmaId` | Turma da qual o professor é responsável. |
| `modo` | `CLASSICA`, `QRCODE` ou `SOM`. |
| `salaId` | Obrigatório no modo `QRCODE` (validação de raio); opcional nos demais. Se informado, precisa existir. |

Ids são ObjectId do MongoDB: 24 caracteres hexadecimais. Outro formato é tratado como inexistente.

Regras, na ordem em que o servidor as verifica:

1. Aluno: `PERMISSAO_NEGADA`.
2. `turmaId` ou `modo` ausentes, ou `modo` desconhecido: `MENSAGEM_INVALIDA`.
3. Turma inexistente ou de outro professor: `PERMISSAO_NEGADA`, com a mesma mensagem nos dois casos,
   para não revelar quais turmas existem.
4. Modo `QRCODE` sem `salaId`, ou `salaId` de sala inexistente: `MENSAGEM_INVALIDA`.
5. Já existe sessão aberta para a turma: `SESSAO_JA_ABERTA`.
- Já existe sessão aberta para a turma: `SESSAO_JA_ABERTA`. A regra é garantida pelo índice único
  parcial `turma_sessao_aberta_unica`, e vale mesmo com pedidos simultâneos em réplicas diferentes.
- Cada sessão recebe um segredo TOTP novo (seção 5.3), enviado só na resposta `sessao.aberta`.
- **Tempo limite:** a sessão aberta há mais de `SESSAO_DURACAO_MAXIMA_MIN` minutos (padrão 240,
  ou 4 horas) é encerrada com motivo `TEMPO_LIMITE`. O servidor varre as sessões vencidas
  periodicamente e também verifica o prazo a cada uso da sessão; uma sessão vencida não impede a
  abertura de outra na mesma turma.

### 4.4 `sessao.encerrar`

```json
{ "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9" }
```

Só o professor dono encerra (`PERMISSAO_NEGADA` para os demais). Sessão inexistente:
`SESSAO_NAO_ENCONTRADA`; já encerrada, pelo professor ou por tempo limite: `SESSAO_ENCERRADA`. A
resposta traz `totalPresentes`, que conta só as presenças com `presente: true`. Os professores que
acompanham a sessão (seção 4.5) também recebem o evento `sessao.encerrada` (seção 5.2).

Depois do encerramento, **nenhum registro é aceito**: toda mensagem com `sessaoId` (`checkin`,
`presenca.marcar`, `presenca.marcar_lote`, `sessao.acompanhar`) verifica primeiro se a sessão está
aberta e responde `SESSAO_ENCERRADA` caso contrário.

### 4.5 `sessao.acompanhar`

Inscreve a conexão para receber os eventos `presenca.atualizada` e `sessao.encerrada` da sessão
(seção 5.2), por exemplo após recarregar a página do painel. A resposta é `sessao.estado` (seção 5.1),
com `ref`.

```json
{ "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9" }
```

```json
{ "v": 1, "tipo": "sessao.acompanhar", "id": "ac1", "ts": 1791370810000,
  "dados": { "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9" } }
```

Regras, na ordem em que o servidor as verifica:

1. `sessaoId` ausente ou em branco: `MENSAGEM_INVALIDA`.
2. Sessão inexistente ou com id em formato inválido: `SESSAO_NAO_ENCONTRADA`.
3. Sessão encerrada, pelo professor ou por tempo limite: `SESSAO_ENCERRADA`. Esta verificação vem
   antes da de papel: um aluno que pede uma sessão já encerrada recebe `SESSAO_ENCERRADA`, não
   `PERMISSAO_NEGADA`.
4. Usuário que não é o professor dono da sessão (aluno ou outro professor): `PERMISSAO_NEGADA`.

- Repetir `sessao.acompanhar` na mesma conexão não duplica eventos: o registro é único por conexão e sessão.
- O registro é feito antes da leitura do estado. Assim, uma presença gravada entre as duas operações
  pode aparecer em `presentes` e também chegar como evento, às vezes antes da própria resposta
  `sessao.estado`. O cliente deve tratar as duas fontes como uma só, deduplicando por `alunoId`.
- **Acompanhante que desconecta:** ao fechar a conexão, por qualquer motivo (fechamento pelo cliente,
  inatividade de 60 s, expiração do token com código 4003 ou queda de rede), o servidor a retira de
  todas as sessões que ela acompanhava. Eventos não são guardados nem reenviados: o que ocorre
  enquanto a conexão está fora não chega a ela. Ao reconectar, o cliente envia `auth` e, de novo,
  `sessao.acompanhar`, que devolve o estado atual.

### 4.6 `presenca.marcar` (modo clássico)

```json
{ "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "alunoId": "66f1b9a1b2c3d4e5f6a7b8c9", "presente": true }
```

Resposta `presenca.atualizada`, com `ref`, cujos campos de `dados` são os mesmos do evento da seção
5.2. Se o próprio professor também acompanha a sessão, a conexão recebe para a mesma marcação a
resposta (com `ref`) e o evento (sem `ref`). Regras, que valem também para `presenca.marcar_lote`, na ordem em que
o servidor as verifica:

1. `presente` ausente ou que não seja booleano: `MENSAGEM_INVALIDA`. Vem antes de qualquer outra regra.
2. Sessão inexistente ou encerrada: `SESSAO_NAO_ENCONTRADA` ou `SESSAO_ENCERRADA`.
3. Só o professor dono da sessão marca: `PERMISSAO_NEGADA` para outro professor ou para aluno.
4. Sessão no modo `CLASSICA`; nos demais modos: `MODO_INCOMPATIVEL`.
5. Aluno fora da turma: `ALUNO_NAO_MATRICULADO`. Um `alunoId` ausente cai neste caso também.
- Cada aluno tem um único registro por sessão: desmarcar grava `presente: false` no mesmo
  documento, com `origem: MANUAL` e `registradoEm` atualizado.

### 4.7 `presenca.marcar_lote` (modo clássico)

Marca todos os matriculados com `presente` e, em seguida, aplica o valor oposto às exceções.

```json
{ "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "presente": true,
  "excecoes": ["66f1b9a1b2c3d4e5f6a7b8c9", "66f1baa1b2c3d4e5f6a7b8c9"] }
```

`excecoes` é opcional. As mesmas regras de ordem e de permissão da seção 4.6 valem aqui. Se alguma
exceção não for aluno da turma, a resposta é
`ALUNO_NAO_MATRICULADO` e **nada** é gravado. O lote sobrescreve marcações anteriores e responde
`presenca.lote_ok` com os totais.

### 4.8 `checkin` (modos QR Code e som)

```json
{
  "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9",
  "token": "492039",
  "canal": "QRCODE",
  "dispositivoId": "7d0b6c0e-6f0e-4b8e-9a51-0c3f3a8c2f11",
  "latitude": -22.8339,
  "longitude": -47.0525,
  "precisaoMetros": 12.5
}
```

| Campo | Descrição |
|---|---|
| `sessaoId` | Id da sessão aberta. |
| `token` | Seis dígitos lidos do QR Code ou decodificados do som. |
| `canal` | `QRCODE` ou `SOM`; deve coincidir com o modo da sessão. Outro valor (ex.: `MANUAL`) é `MODO_INCOMPATIVEL`. |
| `dispositivoId` | Texto não vazio. A aplicação do aluno envia um UUID gerado na primeira execução e guardado no navegador. Um mesmo dispositivo não registra presença para dois alunos na mesma sessão. |
| `latitude`, `longitude`, `precisaoMetros` | Obrigatórios no canal `QRCODE` (`MENSAGEM_INVALIDA` se faltarem ou forem inválidos: latitude fora de -90 a 90, longitude fora de -180 a 180, precisão negativa); ignorados no canal `SOM`. |

Ordem de validação do servidor (`ServicoDeCheckin`):

1. Aluno autenticado: outro papel recebe `PERMISSAO_NEGADA`.
2. `sessaoId` ausente: `MENSAGEM_INVALIDA`. Sessão inexistente ou encerrada: `SESSAO_NAO_ENCONTRADA` ou `SESSAO_ENCERRADA`.
3. `token` ou `dispositivoId` vazios, ou `canal` ausente: `MENSAGEM_INVALIDA`.
4. Canal sem check-in ou diferente do modo da sessão: `MODO_INCOMPATIVEL`.
5. Coordenadas (somente `QRCODE`): ausentes ou inválidas, `MENSAGEM_INVALIDA`.
6. Token: `TOKEN_INVALIDO` ou `TOKEN_EXPIRADO` (seção 5.3).
7. Raio (somente `QRCODE`): `FORA_DO_RAIO`.
8. Matrícula na turma da sessão: `ALUNO_NAO_MATRICULADO`.
9. Aluno já tem presença na sessão: `PRESENCA_DUPLICADA`.
10. Dispositivo já usado por outro aluno na sessão: `DISPOSITIVO_JA_UTILIZADO`.

Os passos 9 e 10 também são garantidos pelos índices únicos `aluno_sessao_unico` e
`sessao_dispositivo_unico`, que cobrem a corrida entre réplicas.

## 5. Mensagens servidor → cliente

### 5.1 Respostas

| Tipo | `dados` |
|---|---|
| `auth.ok` | `{ "usuarioId", "nome", "papel": "PROFESSOR" \| "ALUNO", "expiraEm" }` |
| `pong` | `{}` |
| `sessao.aberta` | `{ "sessaoId", "turmaId", "modo", "abertaEm", "totp" }` (ver 5.3) |
| `sessao.encerrada` | `{ "sessaoId", "motivo": "PROFESSOR" \| "TEMPO_LIMITE", "encerradaEm", "totalPresentes" }` |
| `sessao.estado` | `{ "sessaoId", "modo", "abertaEm", "presentes": [ { "alunoId", "nome", "origem", "registradoEm" } ], "totalMatriculados" }` (seção 4.5) |
| `presenca.lote_ok` | `{ "sessaoId", "totalPresentes", "totalMatriculados" }` |
| `checkin.ok` | `{ "sessaoId", "registradoEm" }` |
| `erro` | `{ "codigo", "mensagem" }` |

`sessao.aberta` é apenas resposta a `sessao.abrir`. `sessao.encerrada` é a resposta a
`sessao.encerrar` e também um evento (5.2).

Respostas levam `ref` e não levam `id`. Exemplos com o envelope completo:

```json
{ "v": 1, "tipo": "sessao.estado", "ref": "ac1", "ts": 1791370810040,
  "dados": { "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "modo": "QRCODE", "abertaEm": 1791370000000,
             "presentes": [
               { "alunoId": "66f1b9a1b2c3d4e5f6a7b8c9", "nome": "Ana Souza",
                 "origem": "QRCODE", "registradoEm": 1791370805170 }
             ],
             "totalMatriculados": 58 } }
```

```json
{ "v": 1, "tipo": "presenca.atualizada", "ref": "m1", "ts": 1791370900100,
  "dados": { "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "alunoId": "66f1baa1b2c3d4e5f6a7b8c9",
             "nomeAluno": "Bruno Lima", "presente": false, "origem": "MANUAL",
             "registradoEm": 1791370900090, "totalPresentes": 30, "totalMatriculados": 58 } }
```

### 5.2 Eventos (push, sem `ref`)

| Tipo | Destinatários | `dados` |
|---|---|---|
| `presenca.atualizada` | Professores acompanhando a sessão | `{ "sessaoId", "alunoId", "nomeAluno", "presente", "origem": "MANUAL" \| "QRCODE" \| "SOM", "registradoEm", "totalPresentes", "totalMatriculados" }` |
| `sessao.encerrada` | Professores acompanhando a sessão | igual à resposta da seção 5.1 |

Eventos não têm `ref`. Exemplo do encerramento por tempo limite:

```json
{ "v": 1, "tipo": "sessao.encerrada", "ts": 1791384400050,
  "dados": { "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "motivo": "TEMPO_LIMITE",
             "encerradaEm": 1791384400000, "totalPresentes": 31 } }
```

**Origem dos eventos:** eles não são enviados pelo roteador de mensagens. O `ObservadorDeMudancas`
de cada réplica acompanha dois fluxos do MongoDB: as presenças (inserção, atualização e substituição)
e as sessões (atualização com `status` `ENCERRADA`). Cada evento é entregue às conexões acompanhando a
sessão naquela réplica (`PainelAoVivo`). Assim, um professor conectado a uma réplica recebe as
presenças registradas em qualquer outra. Por isso a chamada clássica não envia push direto aos
acompanhantes: o evento vem só do fluxo.

- Os fluxos exigem replica set. Se um fluxo cair, ele é reaberto com backoff, retomando a partir do
  último resume token conhecido. Se o histórico já tiver sido descartado (código 286), a reabertura
  parte do momento atual, e os eventos ocorridos no intervalo não são reenviados. Enquanto a
  reconexão não termina, os eventos chegam atrasados.
- `sessao.encerrada` como evento vai só aos acompanhantes. A resposta a `sessao.encerrar` vai só ao
  professor que pediu. Quem encerra a sessão e a acompanha recebe as duas mensagens.
- Ao encerrar a sessão, os acompanhantes são retirados dela: nenhum evento de presença é enviado
  depois do `sessao.encerrada`.

### 5.3 Configuração TOTP

Enviada **somente** ao professor dono da sessão, em `sessao.aberta`, para que a aplicação gere o
token localmente (QR Code e som) sem uma requisição por janela.

```json
{
  "segredo": "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
  "algoritmo": "HmacSHA256",
  "digitos": 6,
  "periodoSegundos": 15,
  "t0": 0
}
```

- Algoritmo conforme a RFC 6238: `T = floor((agora - t0) / periodo)`, HMAC-SHA256 do contador de
  8 bytes, truncamento dinâmico, 6 dígitos. Com `t0` = 0 e período de 15 s, como nesta sessão.
- `segredo` em Base32 (32 caracteres, sem preenchimento), gerado aleatoriamente por sessão (160 bits).
- O servidor aceita a janela **atual** e a **imediatamente anterior**; validade máxima de 30 s.
- Um token que confere com a 2ª a 5ª janela anterior (de 30 s a 75 s atrás) é `TOKEN_EXPIRADO`.
  Token mais antigo, de janela futura, ou com formato diferente de 6 dígitos, é `TOKEN_INVALIDO`.
- Verificação dos parâmetros: o algoritmo do servidor é conferido com os vetores oficiais da RFC 6238
  (Apêndice B, HMAC-SHA256) em `ValidadorTotpTest`, com 8 dígitos e passo de 30 s. O cliente
  do professor confere os mesmos vetores em `totp.test.ts`, e também a truncagem para 6 dígitos.
  Os parâmetros do protocolo (6 dígitos e 15 s) são exercitados pelos testes de check-in
  (`CheckinTest`), que usam a janela atual, a anterior e a expirada.

## 6. Conteúdo transmitido por canal

| Canal | Conteúdo | Observação |
|---|---|---|
| QR Code | `FA1:<sessaoId>:<token>` | Prefixo `FA1` identifica o formato e a versão. Gerado pelo app do professor (`conteudoQr` em `apps/professor/src/lib/totp.ts`). O app do aluno ainda não faz a leitura deste formato no `develop`. |
| Som | Apenas o `token` de 6 dígitos | O aluno obtém o `sessaoId` da sessão ativa da sua turma pela API. O enquadramento do sinal (preâmbulo, FSK, verificação) é definido pela frente E em `docs/audio-modem.md`. |

O `sessaoId` do QR Code é o mesmo `sessaoId` do `checkin`: 24 caracteres hexadecimais.

## 7. Códigos de erro

| Código | Quando ocorre |
|---|---|
| `MENSAGEM_INVALIDA` | JSON malformado, frame binário, `id` com mais de 64 caracteres, campo obrigatório ausente, campo de tipo errado, `presente` não booleano, `modo` ou `canal` desconhecidos, tipo desconhecido ou tipo que o cliente não pode enviar. |
| `VERSAO_NAO_SUPORTADA` | Campo `v` inteiro diferente de `1`. A conexão é fechada com 4002. |
| `AUTH_REQUERIDA` | Mensagem enviada antes de `auth.ok`. |
| `AUTH_INVALIDA` | JWT inválido, expirado ou com assinatura incorreta. A conexão é fechada com 4001. |
| `PERMISSAO_NEGADA` | Papel ou vínculo do usuário não permite a operação. Inclui turma inexistente ou de outro professor em `sessao.abrir`. |
| `SESSAO_NAO_ENCONTRADA` | `sessaoId` inexistente ou em formato inválido. |
| `SESSAO_ENCERRADA` | Sessão já encerrada, pelo professor ou por tempo limite; nenhum registro novo é aceito. |
| `SESSAO_JA_ABERTA` | Já existe sessão aberta para a turma. |
| `MODO_INCOMPATIVEL` | Operação ou canal não corresponde ao modo da sessão. |
| `TOKEN_INVALIDO` | Token não confere com nenhuma janela aceita, ou não tem 6 dígitos. |
| `TOKEN_EXPIRADO` | Token confere com uma das janelas anteriores às aceitas, até 4 janelas além delas (seção 5.3). |
| `FORA_DO_RAIO` | Distância até a sala (Haversine) maior que o raio cadastrado. |
| `ALUNO_NAO_MATRICULADO` | Aluno não pertence à turma da sessão. Em `presenca.marcar_lote`, uma exceção que não é aluno da turma cancela o lote inteiro. |
| `PRESENCA_DUPLICADA` | Já existe presença do aluno nesta sessão (índice único `aluno_sessao_unico`). |
| `DISPOSITIVO_JA_UTILIZADO` | O dispositivo já registrou presença de outro aluno nesta sessão (índice único `sessao_dispositivo_unico`). |
| `LIMITE_EXCEDIDO` | Mais de 60 mensagens em 10 s (seção 1). |
| `ERRO_INTERNO` | Falha inesperada no servidor. Enviado sem `ref`. |

## 8. Códigos de fechamento do WebSocket

| Código | Significado |
|---|---|
| `1000` | Encerramento normal, ou conexão sem tráfego por 60 s (inatividade). |
| `1001` | Servidor reiniciando. É enviado a todas as conexões no desligamento gracioso (SIGTERM); o cliente deve reconectar com backoff. |
| `4001` | Autenticação ausente (prazo de 5 s) ou inválida (`AUTH_INVALIDA`). |
| `4002` | Versão de protocolo não suportada. |
| `4003` | Token JWT expirou durante a conexão. |
| `4008` | Violação de política: limite de mensagens excedido repetidamente (seção 1), ou mais de 64 mensagens pendentes. |

Ao receber `1001` ou uma queda de rede, o cliente reconecta com backoff exponencial
(1 s, 2 s, 4 s… até 30 s), envia `auth` novamente e, se o professor estava acompanhando uma sessão,
envia `sessao.acompanhar` de novo (seção 4.5): o servidor retira a conexão dos acompanhantes ao fechá-la.

## 9. Exemplo completo — check-in por QR Code

Exemplo ilustrativo: os valores de `token` e de horário não correspondem a uma sessão real. O
`presenca.atualizada` é o evento da seção 5.2, recebido pelo professor que acompanha a sessão.

```json
// aluno → servidor
{ "v": 1, "tipo": "checkin", "id": "k9", "ts": 1791370805123,
  "dados": { "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "token": "492039", "canal": "QRCODE",
             "dispositivoId": "7d0b6c0e-6f0e-4b8e-9a51-0c3f3a8c2f11",
             "latitude": -22.8339, "longitude": -47.0525, "precisaoMetros": 12.5 } }

// servidor → aluno
{ "v": 1, "tipo": "checkin.ok", "ref": "k9", "ts": 1791370805180,
  "dados": { "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "registradoEm": 1791370805170 } }

// servidor → professor (evento)
{ "v": 1, "tipo": "presenca.atualizada", "ts": 1791370805190,
  "dados": { "sessaoId": "66f1c2a1b2c3d4e5f6a7b8c9", "alunoId": "66f1b9a1b2c3d4e5f6a7b8c9",
             "nomeAluno": "Ana Souza", "presente": true, "origem": "QRCODE",
             "registradoEm": 1791370805170, "totalPresentes": 31, "totalMatriculados": 58 } }
```

## Histórico

| Versão | Data | Alteração |
|---|---|---|
| 1 | 04/10/2026 | Versão inicial. |
| 1 | 20/10/2026 | Regras de `sessao.abrir` e `sessao.encerrar` e tempo limite da sessão (seções 4.3 e 4.4). Sem mudança de formato. |
| 1 | 04/11/2026 | Revisão contra o servidor de `develop`: limites do servidor (seção 1), ordem de validação de `sessao.abrir`, `presenca.marcar` e `checkin`, situação de `sessao.acompanhar` e dos eventos (PR #96), detalhes do TOTP (seção 5.3), códigos de erro e de fechamento (seções 7 e 8). Após o merge da PR #96, `sessao.acompanhar` e os eventos da seção 5.2 foram documentados como implementados, com a ordem de validação e o comportamento do acompanhante ao desconectar. Sem mudança de formato. |
