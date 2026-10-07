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
| Keep-alive | O cliente envia `ping` a cada 25 s; o servidor fecha conexões sem tráfego por 60 s |

O token **não** é enviado na URL, para não aparecer em logs do Nginx e do navegador.

## 2. Envelope

Toda mensagem, em qualquer direção, segue o mesmo envelope:

```json
{
  "v": 1,
  "tipo": "checkin",
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
| `id` | string | em requisições | Identificador gerado pelo remetente para correlação. Até 64 caracteres. |
| `ref` | string | em respostas | `id` da requisição que originou esta resposta. Ausente em eventos (push). |
| `ts` | inteiro | sim | Instante de envio em milissegundos desde a época Unix (UTC). |
| `dados` | objeto | sim | Corpo específico do tipo. Usa `{}` quando não há conteúdo. |

Regras gerais:

- Campos desconhecidos são ignorados, o que permite evolução compatível dentro da versão 1.
- Datas trafegam como milissegundos Unix (inteiro); identificadores, como strings (ObjectId do MongoDB em hexadecimal).
- Toda requisição recebe **uma** resposta: o tipo de sucesso indicado na tabela ou um `erro`, ambos com `ref` preenchido.

## 3. Ciclo de vida da conexão

```
cliente                                 servidor de sessão
   |---- abre WebSocket /ws ------------------>|
   |---- auth { token } ---------------------->|  (prazo: 5 s após abrir)
   |<--- auth.ok { usuarioId, papel } ---------|
   |---- ping -------------------------------->|
   |<--- pong ---------------------------------|
   |          ... requisições e eventos ...    |
   |<--- close (código 1000 ou 40xx) ----------|
```

Antes de `auth.ok`, qualquer mensagem diferente de `auth` e `ping` é respondida com
`erro` `AUTH_REQUERIDA`. Se o `auth` não chegar em 5 s, a conexão é fechada com código `4001`.

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

### 4.1 `auth`

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9..." }
```

### 4.2 `ping`

`dados` vazio. Também pode ser enviado antes da autenticação.

### 4.3 `sessao.abrir`

```json
{ "turmaId": "66f1c0...", "modo": "QRCODE", "salaId": "66f1c1..." }
```

| Campo | Descrição |
|---|---|
| `turmaId` | Turma da qual o professor é responsável. |
| `modo` | `CLASSICA`, `QRCODE` ou `SOM`. |
| `salaId` | Obrigatório no modo `QRCODE` (validação de raio); opcional nos demais. |

### 4.4 `sessao.encerrar`

```json
{ "sessaoId": "66f1c2..." }
```

### 4.5 `sessao.acompanhar`

Inscreve a conexão para receber os eventos `presenca.atualizada` da sessão, por exemplo após
recarregar a página do painel.

```json
{ "sessaoId": "66f1c2..." }
```

### 4.6 `presenca.marcar` (modo clássico)

```json
{ "sessaoId": "66f1c2...", "alunoId": "66f1b9...", "presente": true }
```

### 4.7 `presenca.marcar_lote` (modo clássico)

Marca todos os matriculados com `presente` e, em seguida, aplica o valor oposto às exceções.

```json
{ "sessaoId": "66f1c2...", "presente": true, "excecoes": ["66f1b9...", "66f1ba..."] }
```

### 4.8 `checkin` (modos QR Code e som)

```json
{
  "sessaoId": "66f1c2...",
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
| `token` | Seis dígitos lidos do QR Code ou decodificados do som. |
| `canal` | `QRCODE` ou `SOM`; deve coincidir com o modo da sessão. |
| `dispositivoId` | UUID gerado pela aplicação do aluno na primeira execução e guardado no navegador. Um mesmo dispositivo não registra presença para dois alunos na mesma sessão. |
| `latitude`, `longitude`, `precisaoMetros` | Obrigatórios no canal `QRCODE`; ignorados no canal `SOM`. |

## 5. Mensagens servidor → cliente

### 5.1 Respostas

| Tipo | `dados` |
|---|---|
| `auth.ok` | `{ "usuarioId", "nome", "papel": "PROFESSOR" \| "ALUNO", "expiraEm" }` |
| `pong` | `{}` |
| `sessao.aberta` | `{ "sessaoId", "turmaId", "modo", "abertaEm", "totp" }` (ver 5.3) |
| `sessao.encerrada` | `{ "sessaoId", "motivo": "PROFESSOR" \| "TEMPO_LIMITE", "encerradaEm", "totalPresentes" }` |
| `sessao.estado` | `{ "sessaoId", "modo", "abertaEm", "presentes": [ { "alunoId", "nome", "origem", "registradoEm" } ], "totalMatriculados" }` |
| `presenca.lote_ok` | `{ "sessaoId", "totalPresentes", "totalMatriculados" }` |
| `checkin.ok` | `{ "sessaoId", "registradoEm" }` |
| `erro` | `{ "codigo", "mensagem" }` |

### 5.2 Eventos (push, sem `ref`)

| Tipo | Destinatários | `dados` |
|---|---|---|
| `presenca.atualizada` | Professores acompanhando a sessão | `{ "sessaoId", "alunoId", "nomeAluno", "presente", "origem": "MANUAL" \| "QRCODE" \| "SOM", "registradoEm", "totalPresentes", "totalMatriculados" }` |
| `sessao.encerrada` | Todos os conectados à sessão | igual à resposta da seção 5.1 |

Os eventos são produzidos a partir dos Change Streams do MongoDB, de modo que um professor
conectado a uma réplica recebe presenças registradas em qualquer outra réplica.

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

- Algoritmo conforme a RFC 6238: `T = floor((agora - t0) / periodo)`, HMAC-SHA256 do contador, truncamento dinâmico, 6 dígitos.
- `segredo` em Base32, gerado aleatoriamente por sessão (160 bits).
- O servidor aceita a janela **atual** e a **imediatamente anterior**; validade máxima de 30 s.
- Cliente e servidor são validados com os mesmos vetores de teste oficiais da RFC 6238.

## 6. Conteúdo transmitido por canal

| Canal | Conteúdo | Observação |
|---|---|---|
| QR Code | `FA1:<sessaoId>:<token>` | Prefixo `FA1` identifica o formato e a versão. |
| Som | Apenas o `token` de 6 dígitos | O aluno obtém o `sessaoId` da sessão ativa da sua turma pela API. O enquadramento do sinal (preâmbulo, FSK, verificação) é definido pela frente E em `docs/audio-modem.md`. |

## 7. Códigos de erro

| Código | Quando ocorre |
|---|---|
| `MENSAGEM_INVALIDA` | JSON malformado, campo obrigatório ausente ou tipo desconhecido. |
| `VERSAO_NAO_SUPORTADA` | Campo `v` diferente de `1`. |
| `AUTH_REQUERIDA` | Mensagem enviada antes de `auth.ok`. |
| `AUTH_INVALIDA` | JWT inválido, expirado ou com assinatura incorreta. |
| `PERMISSAO_NEGADA` | Papel ou vínculo do usuário não permite a operação. |
| `SESSAO_NAO_ENCONTRADA` | `sessaoId` inexistente. |
| `SESSAO_ENCERRADA` | Sessão já encerrada; nenhum registro novo é aceito. |
| `SESSAO_JA_ABERTA` | Já existe sessão aberta para a turma. |
| `MODO_INCOMPATIVEL` | Operação ou canal não corresponde ao modo da sessão. |
| `TOKEN_INVALIDO` | Token não confere com nenhuma janela aceita. |
| `TOKEN_EXPIRADO` | Token confere com uma janela anterior às aceitas. |
| `FORA_DO_RAIO` | Distância até a sala (Haversine) maior que o raio cadastrado. |
| `ALUNO_NAO_MATRICULADO` | Aluno não pertence à turma da sessão. |
| `PRESENCA_DUPLICADA` | Já existe presença do aluno nesta sessão (índice único `{alunoId, sessaoId}`). |
| `DISPOSITIVO_JA_UTILIZADO` | O dispositivo já registrou presença de outro aluno nesta sessão. |
| `LIMITE_EXCEDIDO` | Excesso de mensagens em pouco tempo. |
| `ERRO_INTERNO` | Falha inesperada no servidor. |

## 8. Códigos de fechamento do WebSocket

| Código | Significado |
|---|---|
| `1000` | Encerramento normal. |
| `1001` | Servidor reiniciando; o cliente deve reconectar com backoff. |
| `4001` | Autenticação ausente (prazo de 5 s) ou inválida. |
| `4002` | Versão de protocolo não suportada. |
| `4003` | Token JWT expirou durante a conexão. |
| `4008` | Violação de política (ex.: limite de mensagens excedido repetidamente). |

Ao receber `1001` ou uma queda de rede, o cliente reconecta com backoff exponencial
(1 s, 2 s, 4 s… até 30 s), envia `auth` novamente e, no caso do professor, `sessao.acompanhar`.

## 9. Exemplo completo — check-in por QR Code

```json
// aluno → servidor
{ "v": 1, "tipo": "checkin", "id": "k9", "ts": 1791370805123,
  "dados": { "sessaoId": "66f1c2aa", "token": "492039", "canal": "QRCODE",
             "dispositivoId": "7d0b6c0e-6f0e-4b8e-9a51-0c3f3a8c2f11",
             "latitude": -22.8339, "longitude": -47.0525, "precisaoMetros": 12.5 } }

// servidor → aluno
{ "v": 1, "tipo": "checkin.ok", "ref": "k9", "ts": 1791370805180,
  "dados": { "sessaoId": "66f1c2aa", "registradoEm": 1791370805170 } }

// servidor → professor (evento)
{ "v": 1, "tipo": "presenca.atualizada", "ts": 1791370805190,
  "dados": { "sessaoId": "66f1c2aa", "alunoId": "66f1b9ff", "nomeAluno": "Ana Souza",
             "presente": true, "origem": "QRCODE", "registradoEm": 1791370805170,
             "totalPresentes": 31, "totalMatriculados": 58 } }
```

## Histórico

| Versão | Data | Alteração |
|---|---|---|
| 1 | 04/10/2026 | Versão inicial. |
