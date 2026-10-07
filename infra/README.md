# Infraestrutura

Sobe todo o ambiente com um comando: MongoDB em replica set (3 nós), API, servidor de sessão,
aplicações do professor e do aluno e Nginx como balanceador com suporte a WebSocket.

## Subir

```bash
cd infra
cp .env.example .env       # ajuste JWT_SECRET
docker compose up -d --build
docker compose ps
```

O Nginx fica em `http://localhost:8088` (porta configurável em `NGINX_PORTA`).

| Rota no Nginx | Destino |
|---|---|
| `/api/...` | API REST (Spring Boot, porta 8080) |
| `/ws` | Servidor de sessão (WebSocket, porta 8081) |
| `/professor` | Aplicação do professor (Next.js, porta 3000) |
| `/aluno` | Aplicação do aluno (Next.js, porta 3001); `/` redireciona para cá |
| `/nginx-health` | Verificação do próprio Nginx |

## MongoDB

- Replica set `rs0` com `mongo1` (prioridade 2, primário preferencial), `mongo2` e `mongo3`.
- O serviço `mongo-setup` executa `mongo/init-replica-set.js` uma vez e termina; o script é idempotente.
- O replica set é requisito dos Change Streams, que alimentam o painel ao vivo do professor.
- Apenas o `mongo1` é publicado no host, em `127.0.0.1:27017`. Para conectar de fora do Docker use
  `mongodb://localhost:27017/fast_attendance?directConnection=true`.
- Ambiente de desenvolvimento: o MongoDB roda **sem autenticação** e fica acessível apenas pela
  rede interna do Compose e pelo loopback do host.

## Tolerância a falha

```bash
docker compose up -d --scale api=2          # duas réplicas da API atrás do Nginx
docker compose stop mongo1                  # força nova eleição; mongo2 ou mongo3 assume
docker compose start mongo1
```

O Nginx resolve os nomes dos serviços dinamicamente (`server ... resolve`), então réplicas novas
ou derrubadas entram e saem do balanceamento sem reiniciar o Nginx.

## Limpar

```bash
docker compose down        # mantém os dados
docker compose down -v     # apaga também os volumes do MongoDB
```
