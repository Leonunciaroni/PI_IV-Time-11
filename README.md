# PI_IV-Time-11 — Fast Attendance

Repositório do Projeto Integrador do quarto semestre, PUC Campinas 2026.

O **Fast Attendance** é um sistema de chamada com prova de proximidade para professores do
ensino superior. A cada aula, o professor escolhe um de três modos de confirmação de presença:

1. **Chamada clássica**: marcação manual por lista, com marcação em lote.
2. **QR Code rotativo**: token TOTP (RFC 6238) exibido em QR Code e renovado a cada poucos segundos.
   O aluno confirma a presença dentro do raio da sala.
3. **Onda sonora**: o mesmo token, transmitido em faixa ultrassônica (18–20 kHz).

Os modos 2 e 3 usam a mesma lógica de segurança; muda só o canal que entrega o token ao aluno.

## Estado atual

Este quadro deve ser atualizado a cada marco. Ele descreve o que está no `develop`.

| Parte | Situação |
|---|---|
| **API REST** (`api/`) | Pronta: cadastro e login com JWT, perfil, turmas, matrículas, salas, frequência, estatísticas, histórico do aluno e sessões ativas. Detalhes em [docs/api-rest.md](docs/api-rest.md). |
| **Servidor de sessão** (`sessao/`) e **ponte** (`ponte/`) | Prontos, no modelo cliente-servidor por sockets, com a ponte para os apps web: abrir e encerrar sessão, chamada clássica, check-in pelo canal QR Code e validação do canal sonoro, acompanhamento em tempo real. |
| **Antifraude** (no servidor) | Pronto: token válido, raio até a sala (Haversine), matrícula, uma presença por aluno e uma por dispositivo em cada sessão. O app do aluno envia o check-in por QR Code pela ponte WebSocket/TCP. |
| **Painel ao vivo** | Pronto: o professor vê as presenças chegarem por Change Streams do MongoDB. |
| **App do professor** | Turmas, matrículas, salas, chamada clássica, chamada por QR Code rotativo e painel ao vivo. O modo **Som** aparece desabilitado na tela de abertura de chamada. |
| **App do aluno** | Login, início (turmas, frequência, chamadas abertas), histórico e check-in por QR Code com câmera e geolocalização. A captura por som depende da entrega do modem de áudio. |
| **Modo Som ponta a ponta** | Pendente. O servidor já valida o token, mas a emissão pelo professor (`audio/emissor.mjs`, módulo isolado) não está ligada à tela e o aluno não captura o sinal. Ver [docs/spikes/audio-ultrassom.md](docs/spikes/audio-ultrassom.md). |

## Arquitetura

```
 navegador do professor (/professor)     navegador do aluno (/aluno)
        │ HTTP /api                              │ HTTP /api        │ WebSocket /ws
        └──────────────────┬─────────────────────┴──────────────────┘
                           ▼
                 Nginx (porta 8088 no host)
                    │ /api            │ /ws                 │ /professor, /aluno
                    ▼                 ▼                     ▼
            API (Spring Boot)   Ponte (WebSocket↔TCP)  Apps Next.js
            porta 8080          porta 8082             (professor 3000,
                    │                 │ TCP              aluno 3001)
                    │                 ▼
                    │           Servidor de sessão
                    │           (sockets, porta 8081)
                    └────────┬────────┘
                             ▼
             MongoDB, replica set rs0 (mongo1, mongo2, mongo3)
```

O servidor de sessão segue o modelo cliente-servidor por sockets (TCP, serialização Java, classes
`Servidor`, `AceitadoraDeConexao`, `SupervisoraDeConexao`, `Parceiro` e `Comunicado`). Como o
navegador só fala WebSocket, a **ponte** converte o JSON do protocolo v1 em `Comunicado`s e vice-versa; os
apps não mudam. Veja [docs/servidor-modelo-socket.md](docs/servidor-modelo-socket.md).

A API e o servidor de sessão compartilham o MongoDB e o segredo `JWT_SECRET`: o servidor de sessão
valida o mesmo JWT que a API emite, na mensagem `auth` (recebida pela ponte, no WebSocket). As regras de unicidade
(uma presença por aluno e por sessão, uma sessão aberta por turma) ficam em índices do banco, porque
as duas aplicações escrevem nas mesmas coleções. O diagrama de arquitetura detalhado é tratado na
issue #73 e ainda não existe no repositório.

## Estrutura do repositório

| Caminho | Conteúdo |
|---|---|
| `common/` | Modelos, protocolo de mensagens, `Comunicado`s e JWT compartilhados (Java 21) |
| `api/` | Servidor HTTP: Spring Boot e MongoDB |
| `sessao/` | Servidor de sessão em tempo real, no modelo cliente-servidor por sockets (TCP) |
| `ponte/` | Ponte WebSocket (protocolo v1, JSON) ↔ servidor de sessão (TCP), com Java-WebSocket |
| `apps/professor/` | Aplicação web do professor (Next.js 16, React 19) |
| `apps/aluno/` | Aplicação web do aluno (Next.js 16, React 19) |
| `infra/` | Docker Compose, Nginx, script de replica set e contas de demonstração |
| `audio/` | Módulo de emissão do token sonoro, com testes em Node |
| `spikes/` | Experimentos técnicos (áudio ultrassônico) |
| `docs/` | Protocolo, API, modelo de dados, stack e wireframes |

## Como rodar

### Pré-requisitos

- Docker com Compose v2 (no macOS e no Windows, o Docker Desktop aberto).
- JDK 21 (somente para compilar ou rodar a API ou o servidor de sessão fora do Docker, e para os testes).
- Node.js 24 ou superior (somente para rodar os apps em modo de desenvolvimento).
- `curl`, para o script de contas de demonstração.

### Subir o ambiente

```bash
cd infra
cp .env.example .env          # edite JWT_SECRET (mínimo de 32 bytes)
docker compose up -d --build
docker compose ps             # aguarde os serviços ficarem "healthy"
```

Gere um segredo com `openssl rand -base64 48` e cole o valor em `JWT_SECRET`. O Compose não sobe
sem essa variável.

Variáveis principais do `infra/.env`:

| Variável | Padrão | Uso |
|---|---|---|
| `JWT_SECRET` | (obrigatória) | Assinatura dos JWTs da API e do servidor de sessão |
| `JWT_EXPIRACAO_HORAS` | `12` | Validade do token |
| `PROFESSORES_PERMITIDOS` | `professor@demo.br` | E-mails ou domínios (`@dominio.edu.br`) que podem criar conta de professor |
| `NGINX_PORTA` | `8088` | Porta publicada no host; se mudar, troque também os endereços abaixo |
| `SESSAO_DURACAO_MAXIMA_MIN` | `240` | Sessões abertas por mais que isso são encerradas automaticamente |

### Contas de demonstração

Com o ambiente no ar, crie um professor e dois alunos (senha `demo12345`, só para ambiente local):

```bash
./infra/usuarios-demo.sh            # a partir da raiz do repositório
```

O script é idempotente. Ele cria `professor@demo.br` (professor), `ana@demo.br` e `bruno@demo.br`
(alunos). Se o e-mail do professor não estiver em `PROFESSORES_PERMITIDOS`, o script falha com
essa mensagem.

### Endereços

| Endereço | O que é |
|---|---|
| http://localhost:8088/professor | Aplicação do professor |
| http://localhost:8088/aluno | Aplicação do aluno (`/` redireciona para cá) |
| http://localhost:8088/api | API REST ([docs/api-rest.md](docs/api-rest.md)) |
| ws://localhost:8088/ws | Ponte para o servidor de sessão ([docs/protocolo-v1.md](docs/protocolo-v1.md)) |

Apenas o Nginx é publicado no host, além do `mongo1` em `127.0.0.1:27017`, para ferramentas
locais. Para conectar ao MongoDB de fora do Docker, use
`mongodb://localhost:27017/fast_attendance?directConnection=true`.

### Desenvolvimento dos apps com recarga automática

Deixe o Compose rodando (ele fornece a API, o servidor de sessão e o MongoDB) e rode o app em outro
terminal:

```bash
cd apps/professor && npm install && npm run dev    # http://localhost:3000/professor
cd apps/aluno && npm install && npm run dev        # http://localhost:3001/aluno
```

Em modo de desenvolvimento, `/api` e `/ws` são encaminhados para o Nginx em `localhost:8088`
(variável `API_URL`, se precisar mudar).

### Rodar a API ou o servidor de sessão fora do Docker

Veja [infra/README.md](infra/README.md), seção "Rodar a API ou o servidor de sessão fora do Docker".
Resumo: pare o serviço no Compose (`docker compose stop api`), compile com
`./mvnw -pl api -am package -DskipTests` e rode com `JWT_SECRET=<mesmo valor do .env>`.

### Parar o ambiente

```bash
cd infra
docker compose down        # mantém os dados
docker compose down -v     # apaga também os volumes do MongoDB
```

## Testes

```bash
./mvnw verify                              # common, api e sessao; os testes da API sobem MongoDB em contêiner
cd apps/professor && npm test              # testes unitários do app do professor (Vitest)
cd apps/aluno && npm test                  # leitura QR, localização e transporte de check-in (Vitest)
node --test audio/emissor.test.mjs         # testes do emissor sonoro (Node)
```

Os testes da API usam Testcontainers, então o Docker precisa estar rodando. Para lint e build dos
apps, veja [CONTRIBUTING.md](CONTRIBUTING.md).

## Privacidade e LGPD

Esta seção descreve o que o código faz hoje. Ela segue [docs/modelo-de-dados.md](docs/modelo-de-dados.md).

**Dados pessoais guardados.** Nome, e-mail, matrícula (RA ou registro do professor), hash BCrypt da
senha e os registros de presença de cada aluno. A senha em texto nunca é persistida.

**Coordenadas.** São gravadas somente nas presenças do modo QR Code, e só servem para auditar a
validação de raio. No canal sonoro, as coordenadas enviadas pelo app são ignoradas: não são
validadas nem gravadas. As salas guardam as coordenadas do local, que não são dados pessoais.

**`dispositivoId`.** É um UUID aleatório gerado pelo app do aluno na primeira execução e guardado no
navegador (`localStorage`). Ele é enviado no check-in e gravado na presença do modo QR Code ou sonoro.
Serve para impedir que um mesmo aparelho registre presença para dois alunos na mesma sessão. Não
identifica o aparelho fora do sistema. A marcação manual não grava `dispositivoId`.

**Token de acesso (JWT).** Assinado com HS256 e válido por 12 horas por padrão. O payload contém o
id, o nome e o papel do usuário. O token é assinado, não cifrado: não deve ser exibido nem registrado
em logs. Ele não vai na URL do WebSocket; é enviado na mensagem `auth`.

**Limitações conhecidas (ambiente de desenvolvimento).** O MongoDB do Compose roda sem autenticação e
só é acessível pela rede interna e pelo loopback do host. As contas de demonstração usam senha
conhecida e não devem existir em produção. Não há política de retenção nem rotina de exclusão de
dados implementadas; a exclusão de uma conta ou de presenças ainda não tem endpoint.

## Documentação

Somente documentos que existem no repositório:

| Documento | Conteúdo |
|---|---|
| [docs/protocolo-v1.md](docs/protocolo-v1.md) | Protocolo WebSocket entre os apps e o servidor de sessão, pela ponte (revisado em 04/11/2026) |
| [docs/servidor-modelo-socket.md](docs/servidor-modelo-socket.md) | Servidor de sessão no modelo por sockets e a ponte WebSocket↔TCP |
| [docs/api-rest.md](docs/api-rest.md) | Endpoints REST |
| [docs/modelo-de-dados.md](docs/modelo-de-dados.md) | Coleções, índices do MongoDB e observações de LGPD |
| [docs/stack-apps.md](docs/stack-apps.md) | Decisões de stack das aplicações web |
| [docs/wireframes.md](docs/wireframes.md) | Telas do professor e do aluno |
| [docs/inicio-aluno.md](docs/inicio-aluno.md) | Tela inicial do aluno, frequência e chamadas abertas |
| [docs/checkin-qr.md](docs/checkin-qr.md) | Leitura do QR Code e check-in com geolocalização |
| [docs/historico-aluno.md](docs/historico-aluno.md) | Histórico de frequência do aluno por turma |
| [docs/audio-modem.md](docs/audio-modem.md) | Emissão do token sonoro |
| [docs/spikes/audio-ultrassom.md](docs/spikes/audio-ultrassom.md) | Spike do canal sonoro 18–20 kHz |
| [infra/README.md](infra/README.md) | Ambiente Docker, MongoDB, rotas do Nginx e tolerância a falha |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branches, commits e pull requests |

Diagrama de arquitetura: pendente (issue #73).

## Fluxo de trabalho

Veja [CONTRIBUTING.md](CONTRIBUTING.md) para o modelo de branches, a convenção de commits e como
abrir pull requests.
