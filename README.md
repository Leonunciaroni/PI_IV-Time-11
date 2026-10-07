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

## Fluxo de trabalho

Veja [CONTRIBUTING.md](CONTRIBUTING.md) para o modelo de branches, convenção de commits e
como abrir pull requests.
