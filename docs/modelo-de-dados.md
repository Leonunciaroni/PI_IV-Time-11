# Modelo de dados (MongoDB)

Banco `fast_attendance`, em replica set `rs0`. Os nomes das coleções ficam em
`common/.../modelo/Colecoes.java` e os índices são criados na inicialização da API por
`api/.../config/IndicesMongo.java`, de forma idempotente.

## Coleções

### `usuarios`

| Campo | Tipo | Observação |
|---|---|---|
| `_id` | ObjectId | |
| `nome` | string | |
| `email` | string | Único; armazenado em minúsculas. |
| `senhaHash` | string | Hash BCrypt. A senha em texto nunca é persistida. |
| `papel` | string | `PROFESSOR` ou `ALUNO`. |
| `matricula` | string | RA do aluno ou registro do professor. |
| `criadoEm` | date | |

### `turmas`

| Campo | Tipo | Observação |
|---|---|---|
| `_id` | ObjectId | |
| `nome` | string | Ex.: "Turma A". |
| `disciplina` | string | |
| `horarioPadrao` | string | Ex.: "Seg 19:00". |
| `professorId` | string | Referência a `usuarios`. |
| `criadaEm` | date | |

### `matriculas`

| Campo | Tipo | Observação |
|---|---|---|
| `_id` | ObjectId | |
| `turmaId` | string | |
| `alunoId` | string | |
| `criadaEm` | date | |

### `salas`

| Campo | Tipo | Observação |
|---|---|---|
| `_id` | ObjectId | |
| `nome` | string | Ex.: "H15 - Sala 203". |
| `latitude`, `longitude` | double | Coordenadas usadas na fórmula de Haversine. |
| `raioMetros` | double | Distância máxima aceita no modo QR Code. |

### `sessoes`

Uma sessão é uma aula aberta pelo professor em um dos três modos.

| Campo | Tipo | Observação |
|---|---|---|
| `_id` | ObjectId | É o `sessaoId` do protocolo. |
| `turmaId` | string | |
| `professorId` | string | |
| `modo` | string | `CLASSICA`, `QRCODE` ou `SOM`. Alterado por `sessao.trocar_modo`. |
| `salaId` | string | Obrigatório no modo `QRCODE`. Pode ser trocado junto com o modo. |
| `segredoTotp` | string | Base32, gerado por sessão; enviado só ao professor dono. |
| `status` | string | `ABERTA` ou `ENCERRADA`. |
| `abertaEm`, `encerradaEm` | date | |
| `motivoEncerramento` | string | `PROFESSOR` ou `TEMPO_LIMITE`. |
| `modoAlteradoEm` | date | Instante da última troca de modo. Gravado só pelo servidor de sessão; ausente em sessão que nunca teve o modo trocado. |
| `trocasDeModo` | array | Histórico das trocas, uma entrada por troca, na ordem em que ocorreram. Cada item tem `de` e `para` (modos), `salaId` (sala vigente após a troca) e `em` (date). Ausente em sessão sem troca. |

### `presencas`

| Campo | Tipo | Observação |
|---|---|---|
| `_id` | ObjectId | |
| `sessaoId` | string | |
| `turmaId` | string | Desnormalizado para os relatórios por turma. |
| `alunoId` | string | |
| `presente` | bool | No modo clássico o professor pode desmarcar (`false`). |
| `origem` | string | `MANUAL`, `QRCODE` ou `SOM`. |
| `dispositivoId` | string | UUID do navegador do aluno; ausente na marcação manual. |
| `latitude`, `longitude` | double | Apenas no modo QR Code. |
| `registradoEm` | date | |

## Índices

| Coleção | Nome | Campos | Regra |
|---|---|---|---|
| `usuarios` | `email_unico` | `{email: 1}` | Único. |
| `turmas` | `professor` | `{professorId: 1}` | Listagem das turmas do professor. |
| `matriculas` | `turma_aluno_unico` | `{turmaId: 1, alunoId: 1}` | Único: o aluno é matriculado uma vez por turma. |
| `matriculas` | `aluno` | `{alunoId: 1}` | Turmas do aluno. |
| `sessoes` | `turma_abertura` | `{turmaId: 1, abertaEm: -1}` | Histórico de aulas da turma. |
| `sessoes` | `turma_sessao_aberta_unica` | `{turmaId: 1}` parcial em `status = ABERTA` | Único: no máximo uma sessão aberta por turma. |
| `presencas` | `aluno_sessao_unico` | `{alunoId: 1, sessaoId: 1}` | **Único: impede presença duplicada** (objetivo do relatório). |
| `presencas` | `sessao_dispositivo_unico` | `{sessaoId: 1, dispositivoId: 1}` parcial em `dispositivoId` string | Único: um dispositivo não registra presença de dois alunos na mesma sessão. |
| `presencas` | `turma_aluno` | `{turmaId: 1, alunoId: 1}` | Relatório de frequência por aluno. |

As regras de unicidade ficam no banco, e não apenas no código, porque a API e o servidor de
sessão escrevem nas mesmas coleções a partir de várias réplicas. Uma violação gera erro de chave
duplicada (código 11000), que o servidor traduz para `PRESENCA_DUPLICADA` ou
`DISPOSITIVO_JA_UTILIZADO` no protocolo.

## LGPD

- Coordenadas são guardadas apenas nas presenças do modo QR Code e servem exclusivamente para
  auditar a validação de raio.
- O `dispositivoId` é um UUID aleatório gerado pela aplicação; não identifica o aparelho fora do
  sistema.
