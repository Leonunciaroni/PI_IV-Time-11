# PI_IV-Time-11 — Fast Attendance

Repositório dedicado ao Projeto Integrador do quarto semestre, PUC Campinas 2026.

O **Fast Attendance** é um sistema de chamada com prova de proximidade para professores do
ensino superior. O professor escolhe, a cada aula, um de três modos de confirmação de presença:

1. **Chamada clássica** — marcação manual por lista, com marcação em lote.
2. **QR Code rotativo** — token TOTP exibido em QR Code, renovado a cada poucos segundos.
3. **Onda sonora** — o mesmo token transmitido em faixa ultrassônica (18–20 kHz).

Os modos 2 e 3 compartilham a mesma lógica de segurança (TOTP, RFC 6238); muda apenas o canal
usado para entregar o token ao aluno.

## Estrutura do repositório

| Caminho | Conteúdo | Frente |
|---|---|---|
| `common/` | Modelos, protocolo de mensagens e JWT compartilhados | A, B |
| `api/` | Servidor HTTP (Spring Boot + MongoDB) | A |
| `sessao/` | Servidor de sessão em tempo real (Java-WebSocket) | B |
| `apps/professor/` | Aplicação web do professor (Next.js) | C |
| `apps/aluno/` | Aplicação web do aluno (Next.js) | D |
| `infra/` | Docker Compose, Nginx e scripts do MongoDB | E |
| `spikes/` | Experimentos técnicos (ex.: áudio ultrassônico) | E |
| `docs/` | Protocolo, arquitetura, stack e wireframes | Todos |

## Como rodar

Pré-requisitos: Docker, JDK 21+ e Node.js 24+ (o Maven vem pelo wrapper `./mvnw`).

```bash
cd infra
cp .env.example .env          # defina JWT_SECRET
docker compose up -d --build
```

| Endereço | O quê |
|---|---|
| http://localhost:8088/professor | Aplicação do professor |
| http://localhost:8088/aluno | Aplicação do aluno |
| http://localhost:8088/api | API REST ([docs/api-rest.md](docs/api-rest.md)) |
| ws://localhost:8088/ws | Servidor de sessão ([docs/protocolo-v1.md](docs/protocolo-v1.md)) |

Para desenvolver um app com recarga automática, deixe o Compose rodando e use `npm run dev` em
`apps/professor` (porta 3000) ou `apps/aluno` (porta 3001); as chamadas a `/api` são encaminhadas
ao Compose. Para a API ou o servidor de sessão fora do Docker, veja [infra/README.md](infra/README.md).

Testes: `./mvnw verify` na raiz (os testes da API sobem um MongoDB com Testcontainers).

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/protocolo-v1.md](docs/protocolo-v1.md) | Mensagens WebSocket entre os apps e o servidor de sessão |
| [docs/api-rest.md](docs/api-rest.md) | Endpoints REST |
| [docs/modelo-de-dados.md](docs/modelo-de-dados.md) | Coleções e índices do MongoDB |
| [docs/stack-apps.md](docs/stack-apps.md) | Decisões de stack das aplicações web |
| [docs/wireframes.md](docs/wireframes.md) | Telas do professor e do aluno |
| [docs/inicio-aluno.md](docs/inicio-aluno.md) | Tela inicial do aluno, frequência e chamadas abertas |
| [docs/spikes/audio-ultrassom.md](docs/spikes/audio-ultrassom.md) | Spike do canal sonoro 18–20 kHz |

## Fluxo de trabalho

Veja [CONTRIBUTING.md](CONTRIBUTING.md) para o modelo de branches, convenção de commits e
como abrir pull requests.
