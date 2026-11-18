# API REST

Base: `/api` (via Nginx, `http://localhost:8088/api`). Corpo em JSON. Erros seguem o formato
Problem Details (RFC 9457), com mensagens em português no campo `detail`.

## Autenticação

Rotas protegidas exigem `Authorization: Bearer <jwt>`. O mesmo JWT autentica o WebSocket do
servidor de sessão (mensagem `auth`, ver [protocolo-v1.md](protocolo-v1.md)).

- Assinatura HS256 com o segredo `JWT_SECRET` (mínimo de 32 bytes), compartilhado com o servidor de sessão.
- Validade padrão de 12 horas (`JWT_EXPIRACAO_HORAS`).
- Claims: `iss` = `fast-attendance`, `sub` = id do usuário, `nome`, `papel`, `iat`, `exp`.
- Senhas armazenadas apenas como hash BCrypt.

### `POST /api/auth/registro` — público

Cria uma conta de aluno ou de professor. O cadastro de alunos é aberto; o de professores só é aceito
para e-mails autorizados na variável de ambiente `PROFESSORES_PERMITIDOS`, uma lista separada por
vírgulas de e-mails completos (`matheus@puc.br`) ou domínios iniciados por `@`
(`@puc-campinas.edu.br`), sem diferenciar maiúsculas. Com a lista vazia, nenhum professor se
cadastra pela API.

```json
{ "nome": "Ana Souza", "email": "ana@puc.br", "senha": "senha-segura",
  "papel": "ALUNO", "matricula": "24001234" }
```

| Campo | Regra |
|---|---|
| `nome` | Obrigatório, até 120 caracteres. |
| `email` | Obrigatório, formato de e-mail; normalizado para minúsculas; único. |
| `senha` | De 8 a 72 caracteres (limite do BCrypt). |
| `papel` | `PROFESSOR` ou `ALUNO`. |
| `matricula` | Obrigatório, até 30 caracteres. |

| Status | Quando |
|---|---|
| `201` | Conta criada. Corpo: `UsuarioResponse`. |
| `400` | Campos inválidos; o corpo traz `campos` com a mensagem de cada campo. |
| `403` | Papel `PROFESSOR` com e-mail fora de `PROFESSORES_PERMITIDOS`. Verificado antes do `409`. |
| `409` | E-mail já cadastrado. |

### `POST /api/auth/login` — público

```json
{ "email": "ana@puc.br", "senha": "senha-segura" }
```

Resposta `200`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "expiraEm": "2026-10-08T00:00:00Z",
  "usuario": { "id": "66f1b9ff...", "nome": "Ana Souza", "email": "ana@puc.br",
               "papel": "ALUNO", "matricula": "24001234" }
}
```

`401` com `"detail": "E-mail ou senha incorretos."` tanto para e-mail inexistente quanto para senha
errada, com o mesmo tempo de resposta, para não revelar quais e-mails estão cadastrados.

### `GET /api/auth/me` — autenticado

Devolve o `UsuarioResponse` do dono do token. `401` sem token ou com token inválido/expirado.

### `PATCH /api/auth/me`

Aluno ou professor autenticado edita somente o próprio perfil. Campos opcionais:
`nome` (não vazio, até 120), `email` (válido, único, até 254), `novaSenha` (8–72 caracteres,
até 72 bytes UTF-8) e `senhaAtual`. Trocar a senha exige `senhaAtual` correta.
Campos omitidos são preservados; papel e matrícula não são editáveis.

```json
{ "nome": "Ana Souza", "email": "ana.nova@instituicao.edu.br" }
```

```json
{ "senhaAtual": "senha-anterior", "novaSenha": "nova-senha-segura" }
```

Resposta `200` com `UsuarioResponse`, sem senha ou hash. O cliente pode usar a resposta
para atualizar a sessão exibida. `400` para campos inválidos, pedido vazio ou senha atual
incorreta; `401` sem autenticação; `409` para e-mail em uso. A atualização é atômica:
uma recusa não altera os demais campos. O JWT atual mantém sua validade original.

## Turmas

Todas as rotas de `/api/turmas` exigem o papel `PROFESSOR`: sem token a resposta é `401`, e com o
token de um aluno é `403`. O professor só enxerga as próprias turmas; o id de uma turma de outro
professor, ou que não existe, responde `404` com `"detail": "Turma não encontrada"`, para não
revelar quais turmas existem.

### `POST /api/turmas`

```json
{ "nome": "Turma A", "disciplina": "Engenharia de Software I", "horarioPadrao": "Seg 19:00" }
```

| Campo | Regra |
|---|---|
| `nome` | Obrigatório. |
| `disciplina` | Obrigatório. |
| `horarioPadrao` | Obrigatório, no formato `Dia HH:MM` (ver abaixo). |

Espaços nas pontas são removidos. Resposta `201` com `TurmaResponse`; `400` traz `campos` com a
mensagem de cada campo inválido.

**Formato do `horarioPadrao`:** o dia da semana, um espaço e o horário, como em `Seg 19:00`.

| Parte | Regra |
|---|---|
| Dia | De segunda a domingo, abreviado (`Seg`, `Ter`, `Qua`, `Qui`, `Sex`, `Sáb`, `Dom`) ou por extenso (`Segunda`, `Terça`, `Quarta`, `Quinta`, `Sexta`, `Sábado`, `Domingo`). Maiúsculas e acentos não importam: `terca` e `SABADO` valem. |
| Hora | Dois dígitos, de `00` a `23`. |
| Minuto | Dois dígitos, de `00` a `59`. |

O valor é sempre guardado e devolvido no formato abreviado: `segunda 19:00` vira `Seg 19:00` e
`sabado 08:30` vira `Sáb 08:30`. Textos fora do padrão (`à noite`, `Seg 24:00`, `Seg 9:00`,
`19:00`) respondem `400`, com a orientação de formato em `campos.horarioPadrao`.

### `GET /api/turmas`

Turmas do professor autenticado, ordenadas por disciplina e nome (lista vazia se não houver
nenhuma). Resposta `200` com uma lista de `TurmaResponse`.

### `GET /api/turmas/{id}`

Resposta `200` com `TurmaResponse`; `404` se a turma não for do professor ou não existir.

### `GET /api/turmas/{id}/alunos`

Alunos matriculados na turma, em ordem alfabética. Resposta `200` com uma lista de `AlunoResponse`.

### `GET /api/turmas/{id}/candidatos?busca=`

Alunos que **ainda não estão** na turma, para a tela de matrícula. `busca` é opcional e casa com
qualquer parte do nome ou da matrícula, sem diferenciar maiúsculas; o texto é tratado como literal,
não como expressão regular. Devolve os alunos em ordem alfabética; professores nunca aparecem na
lista.

### `POST /api/turmas/{id}/matriculas`

Matricula vários alunos de uma vez.

```json
{ "alunoIds": ["66f1b9ff...", "66f1ba01..."] }
```

`alunoIds` é obrigatório, deve ter ao menos um id, e repetições são ignoradas. Resposta `200`:

```json
{ "matriculados": 1, "jaMatriculados": 1, "totalAlunos": 2 }
```

Quem já está na turma não gera erro: conta em `jaMatriculados`. Se algum id não existir ou não for
de um aluno, a resposta é `400` (`"A lista tem alunos que não existem."`) e **nenhum** aluno da
lista é matriculado. Se a lista for vazia, `400` com `campos`.

### `DELETE /api/turmas/{id}/matriculas/{alunoId}`

Tira o aluno da turma. Resposta `204` sem corpo. Se o aluno não estiver matriculado na turma, a
resposta é `404` com `"detail": "Matrícula não encontrada"`.

As presenças que o aluno já tinha continuam gravadas, mas ele sai da lista de alunos e das
estatísticas da turma, o histórico dele na turma passa a responder `404` e ele volta a aparecer em
`candidatos`. As matrículas dele em outras turmas não mudam.

### `POST /api/turmas/{id}/importacao`

Matricula na turma os alunos listados em um CSV. Requisição `multipart/form-data` com o arquivo no
campo `arquivo`. O cabeçalho (primeira linha) precisa ter as colunas `nome`, `email` e `matricula`
(`ra` também vale para a matrícula), em qualquer ordem; aceita vírgula ou ponto e vírgula, e o UTF-8
do Excel com BOM. Limite de 2000 linhas por arquivo.

```csv
nome,email,matricula
Ana Souza,ana@puc.br,24001234
```

Só entram alunos que **já têm conta**: o aluno é encontrado pelo e-mail (sem diferenciar maiúsculas)
e a matrícula do arquivo precisa ser a cadastrada. Resposta `200`:

```json
{
  "importados": 1,
  "jaMatriculados": 1,
  "rejeitados": [{ "linha": 4, "motivo": "Aluno não cadastrado." }],
  "totalAlunos": 2
}
```

`linha` conta o cabeçalho como 1. Motivos de rejeição: campo obrigatório vazio, e-mail inválido,
e-mail repetido no arquivo, aluno não cadastrado (inclui e-mail de professor) e matrícula que não
confere. Uma linha rejeitada não impede as demais. É idempotente: quem já está na turma conta em
`jaMatriculados`. Arquivo ausente, vazio, sem as colunas ou com mais de 2000 linhas responde `400`;
turma inexistente ou de outro professor, `404`. Um arquivo que não dá para ler como CSV (aspas fora
do lugar, como em `Ana "Aninha" Souza`, ou um arquivo binário) também responde `400`, com
`"Não foi possível ler o arquivo como CSV. Confira as aspas e o separador."`. Nenhum aluno do
arquivo é importado nesse caso.

### `GET /api/turmas/{id}/estatisticas`

Números do dashboard de frequência da turma (tela P9). Só entram no cálculo as aulas já
**encerradas**. Responde `404` se a turma não existe ou é de outro professor.

| Cálculo | Regra |
|---|---|
| Frequência da aula | Presentes ÷ alunos matriculados hoje. Quem não tem registro conta como ausente, e a presença desmarcada (`presente: false`) não conta. |
| Frequência do aluno | Aulas com presença ÷ aulas dadas. |
| Frequência média | Média dos percentuais das aulas. |
| Abaixo do mínimo | Aluno com menos de 75% (exatamente 75% não entra), do menor para o maior percentual. |
| Uso por modo | Número de aulas dadas em cada modo; os três modos sempre aparecem, mesmo com zero. |

Os percentuais têm uma casa decimal. Turma sem aulas ou sem alunos devolve zeros e listas vazias.

### `GET /api/turmas/{id}/exportacao/consolidado`

Baixa o CSV com a frequência acumulada da turma: uma linha por aluno matriculado, em ordem
alfabética, com `Nome`, `Matrícula`, `Presenças`, `Aulas`, `Frequência (%)` e `Situação`
(`Regular`, `Abaixo de 75%` ou `Sem aulas`). Só contam as aulas encerradas, com as mesmas regras do
dashboard.

### `GET /api/turmas/{id}/exportacao/aulas/{sessaoId}`

Baixa o CSV da chamada de uma aula da turma: todos os alunos matriculados com `Nome`, `Matrícula`,
`Situação` (`Presente` ou `Ausente`), `Origem` (`MANUAL`, `QRCODE` ou `SOM`) e `Registrado em`
(horário de Brasília).

Os dois arquivos vêm como `text/csv` em UTF-8 com BOM, ponto e vírgula como separador e vírgula
decimal, para abrir direto no Excel em português. O nome sugerido em `Content-Disposition` usa o
nome da turma (`frequencia-<turma>.csv`, `chamada-<turma>-<data>.csv`). Textos que começam com `=`,
`+`, `-` ou `@` ganham um apóstrofo na frente, para o Excel não executá-los como fórmula. Turma
inexistente ou de outro professor, ou aula que não é da turma, responde `404`; aluno recebe `403`.

### `GET /api/turmas/{id}/frequencia/{alunoId}`

Histórico de um aluno na turma: todas as aulas encerradas e se ele esteve presente em cada uma,
mais o percentual acumulado. Usado na tela de histórico do aluno. Aceita professor e aluno.

| Quem consulta | Regra | Resposta |
|---|---|---|
| Professor dono da turma | Vê qualquer aluno matriculado. | `200` |
| Professor de outra turma | A turma responde como inexistente. | `404` |
| Aluno | Só o próprio histórico (`alunoId` igual ao do token). | `200` |
| Aluno pedindo outro aluno | `"Você só pode ver o seu próprio histórico."` | `403` |
| Aluno não matriculado na turma | Responde como inexistente. | `404` |

Usa as mesmas regras do cálculo de estatísticas: só aulas encerradas, e quem não tem registro ou foi
desmarcado conta como ausente. Menos de 75% marca `abaixoDoMinimo`; turma sem aulas devolve zeros.

## `TurmaResponse`

```json
{ "id": "66f1c0aa...", "nome": "Turma A", "disciplina": "Engenharia de Software I",
  "horarioPadrao": "Seg 19:00", "totalAlunos": 58, "criadaEm": "2026-10-20T22:10:00Z" }
```

## `AlunoResponse`

```json
{ "id": "66f1b9ff...", "nome": "Ana Souza", "matricula": "24001234" }
```

O e-mail do aluno não é exposto ao professor.

## `EstatisticasResponse`

```json
{
  "aulasDadas": 12, "totalAlunos": 30, "frequenciaMedia": 87.5, "minimo": 75,
  "aulas": [
    { "sessaoId": "66f1d0aa...", "data": "2026-10-19T22:00:00Z", "modo": "QRCODE",
      "presentes": 27, "matriculados": 30, "percentual": 90.0 }
  ],
  "abaixoDoMinimo": [
    { "alunoId": "66f1b9ff...", "nome": "Bruno Lima", "matricula": "24005678",
      "presencas": 8, "aulas": 12, "percentual": 66.7 }
  ],
  "usoPorModo": { "CLASSICA": 2, "QRCODE": 7, "SOM": 3 }
}
```

`aulas` vem em ordem cronológica e `data` é a abertura da aula.

## `HistoricoResponse`

```json
{
  "turmaId": "66f1c0aa...", "alunoId": "66f1b9ff...", "nome": "Bruno Lima", "matricula": "24005678",
  "aulasDadas": 12, "presencas": 8, "percentual": 66.7, "minimo": 75, "abaixoDoMinimo": true,
  "aulas": [
    { "sessaoId": "66f1d0aa...", "data": "2026-10-19T22:00:00Z", "modo": "QRCODE", "presente": true }
  ]
}
```

`aulas` vem em ordem cronológica e `data` é a abertura da aula.

## `UsuarioResponse`

```json
{ "id": "66f1b9ff...", "nome": "Ana Souza", "email": "ana@puc.br",
  "papel": "ALUNO", "matricula": "24001234" }
```

O hash da senha nunca é exposto.

## Salas

Salas físicas do campus, usadas na validação de distância do modo QR Code. As salas são
compartilhadas: qualquer professor vê e cadastra salas. As rotas de `/api/salas` exigem o papel
`PROFESSOR`: sem token a resposta é `401`, e com o token de um aluno é `403`.

### `POST /api/salas`

```json
{ "nome": "H15 - Sala 203", "latitude": -22.8337, "longitude": -47.0525, "raioMetros": 30 }
```

| Campo | Regra |
|---|---|
| `nome` | Obrigatório; espaços nas pontas são removidos. |
| `latitude` | Obrigatório, de `-90` a `90` (graus decimais). |
| `longitude` | Obrigatório, de `-180` a `180` (graus decimais). |
| `raioMetros` | Obrigatório, maior que zero. Distância máxima aceita entre o aluno e a sala. |

Resposta `201` com `SalaResponse`; `400` traz `campos` com a mensagem de cada campo inválido.

### `GET /api/salas`

Todas as salas, em ordem alfabética do nome (lista vazia se não houver nenhuma). Resposta `200`
com uma lista de `SalaResponse`.

## `SalaResponse`

```json
{ "id": "66f1c3bb...", "nome": "H15 - Sala 203", "latitude": -22.8337, "longitude": -47.0525,
  "raioMetros": 30.0 }
```

## Início do aluno

### `GET /api/aluno/turmas`

Exige JWT com papel `ALUNO`. Sem autenticação responde `401`; professor recebe `403`.
A identidade vem do token, sem aceitar outro `alunoId` informado pelo cliente.

Lista somente turmas nas quais o aluno autenticado está matriculado, ordenadas por disciplina
e nome. Sem matrículas, retorna `[]`. Consulta turmas, aulas e presenças em lote.

```json
[
  {
    "id": "turma-id", "nome": "Turma A", "disciplina": "Engenharia de Software I",
    "horarioPadrao": "Seg 19:00", "aulasDadas": 20, "presencas": 19,
    "percentual": 95.0, "minimo": 75, "abaixoDoMinimo": false,
    "chamadaAberta": { "id": "sessao-id", "modo": "QRCODE", "abertaEm": "2026-10-26T22:00:00Z" }
  }
]
```

Frequência segue o histórico existente: só aulas encerradas, sem presença ou `presente: false`
conta como ausência. Sem aulas encerradas, `percentual` vale `0` e `abaixoDoMinimo` é `false`;
a interface apresenta “—” para distinguir falta de dados de frequência zero.

`chamadaAberta` é `null` quando não existe aula aberta. O modo pode ser `CLASSICA`, `QRCODE`
ou `SOM`. A resposta nunca inclui o segredo TOTP. O histórico detalhado continua disponível
em `GET /api/turmas/{id}/frequencia/{alunoId}` com as autorizações já documentadas.

## Aulas e sessões ativas

### `GET /api/turmas/{id}/aulas`

Somente professor dono da turma. Lista aulas abertas e encerradas, da mais recente para a
mais antiga, com `id`, `turmaId`, `modo`, `status`, `abertaEm`, `encerradaEm` e `totalPresentes`.
`encerradaEm` é `null` na aula aberta. Conta apenas registros com `presente: true`.
Sem aulas retorna `[]`; turma inexistente ou de outro professor responde `404`; aluno, `403`.

### `GET /api/sessoes/ativas`

Somente aluno autenticado. Retorna as chamadas abertas das turmas em que ele está matriculado,
da mais recente para a mais antiga, nos modos `CLASSICA`, `QRCODE` ou `SOM`:

```json
[{ "sessaoId": "sessao-id", "turmaId": "turma-id", "turma": "Turma A",
   "disciplina": "Engenharia de Software I", "modo": "SOM", "abertaEm": "2026-10-29T22:00:00Z" }]
```

Nunca retorna segredos TOTP ou dados de presença de outros alunos. Sem matrículas/chamadas,
responde `[]`; professor recebe `403`. Ambas as consultas exigem JWT; sem token respondem `401`.

## Saúde

`GET /actuator/health` (direto na porta 8080 do contêiner; não é exposto pelo Nginx).
