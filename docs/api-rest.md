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
| `nome` | Obrigatório, até 120 caracteres. |
| `disciplina` | Obrigatório, até 120 caracteres. |
| `horarioPadrao` | Obrigatório, até 60 caracteres. |

Espaços nas pontas são removidos. Resposta `201` com `TurmaResponse`; `400` traz `campos` com a
mensagem de cada campo inválido.

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
não como expressão regular. Devolve no máximo 20 alunos, em ordem alfabética. Professores nunca
aparecem na lista.

### `POST /api/turmas/{id}/matriculas`

Matricula vários alunos de uma vez.

```json
{ "alunoIds": ["66f1b9ff...", "66f1ba01..."] }
```

`alunoIds` é obrigatório, tem de 1 a 200 ids e repetições são ignoradas. Resposta `200`:

```json
{ "matriculados": 1, "jaMatriculados": 1, "totalAlunos": 2 }
```

Quem já está na turma não gera erro: conta em `jaMatriculados`. Se algum id não existir ou não for
de um aluno, a resposta é `400` (`"A lista tem alunos que não existem."`) e **nenhum** aluno da
lista é matriculado. Se a lista for vazia ou passar de 200 ids, `400` com `campos`.

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

## `UsuarioResponse`

```json
{ "id": "66f1b9ff...", "nome": "Ana Souza", "email": "ana@puc.br",
  "papel": "ALUNO", "matricula": "24001234" }
```

O hash da senha nunca é exposto.

## Saúde

`GET /actuator/health` (direto na porta 8080 do contêiner; não é exposto pelo Nginx).
