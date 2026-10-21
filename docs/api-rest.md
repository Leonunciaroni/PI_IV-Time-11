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

## Saúde

`GET /actuator/health` (direto na porta 8080 do contêiner; não é exposto pelo Nginx).
