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

Cria uma conta de professor ou aluno.

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

## `UsuarioResponse`

```json
{ "id": "66f1b9ff...", "nome": "Ana Souza", "email": "ana@puc.br",
  "papel": "ALUNO", "matricula": "24001234" }
```

O hash da senha nunca é exposto.

## Saúde

`GET /actuator/health` (direto na porta 8080 do contêiner; não é exposto pelo Nginx).
