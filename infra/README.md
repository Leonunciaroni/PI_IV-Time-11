# Infraestrutura

Sobe todo o ambiente com um comando: MongoDB em replica set (3 nós), API, servidor de sessão (TCP,
sem porta publicada), ponte WebSocket, aplicações do professor e do aluno e Nginx como balanceador com
suporte a WebSocket.

## Subir

```bash
cd infra
cp .env.example .env       # ajuste JWT_SECRET
docker compose up -d --build
docker compose ps
```

O Nginx fica em `http://localhost:8088` (porta configurável em `NGINX_PORTA`).

Contas de demonstração (um professor e dois alunos, senha `demo12345`). O e-mail do professor,
`professor@demo.br`, precisa estar em `PROFESSORES_PERMITIDOS` (já está no `.env.example`):

```bash
./usuarios-demo.sh
```

### Dados de demonstração (dashboard e apresentação)

Para o dashboard ter números que pareçam reais, rode, com o Compose no ar:

```bash
./dados-demo.sh            # ou ./dados-demo.sh http://localhost:<NGINX_PORTA>
```

O script cria as contas de `usuarios-demo.sh` e mais 60 alunos (`aluno01@demo.br` a
`aluno60@demo.br`, matrícula `24100001` a `24100060`), todos com a senha `demo12345`, pela API. Depois,
`mongo/dados-demo.js` grava direto no banco, pelo `mongosh` do `mongo1`:

| O quê | Conteúdo |
|---|---|
| Turmas do `professor@demo.br` | Engenharia de Software I · Turma A (Seg 19:00, 32 alunos, com Ana e Bruno) e Estrutura de Dados · Turma B (Qua 21:00, 31 alunos, com Ana) |
| Salas | H15 - Sala 203 (raio 60 m) e H15 - Laboratório 4 (raio 40 m), com coordenadas do campus I |
| Aulas encerradas | 12 na Turma A (Clássica 2 · QR Code 7 · Som 3) e 10 na Turma B, uma por semana até a última antes de hoje, no dia e horário da turma |
| Presenças | Nos três modos: `MANUAL` na clássica (com algumas desmarcadas), `QRCODE` com coordenadas dentro do raio e `SOM`, as duas com `dispositivoId`. Uns poucos alunos faltam de propósito e ficam abaixo de 75% |

As aulas e presenças vão direto no banco porque a API não abre aulas (quem abre é o servidor de
sessão, ao vivo). O script é idempotente: contas que já existem são mantidas, as
turmas são encontradas pelo nome e pela disciplina (outras turmas do professor não são alteradas) e as salas pelo nome, as matrículas não se repetem e uma turma que já tem aula encerrada não ganha
aulas novas. Para gerar as aulas de novo, com as datas recalculadas a partir de hoje, apague os
volumes (`docker compose down -v`) e rode de novo.

Com o banco autenticado (#54), o script usa o usuário da aplicação (`MONGO_APP_USUARIO` e
`MONGO_APP_SENHA` do container `mongo1`). A URI pode ser trocada pela variável `MONGO_URI`.

| Rota no Nginx | Destino |
|---|---|
| `/api/...` | API REST (Spring Boot, porta 8080) |
| `/ws` | Ponte WebSocket (porta 8082), que fala com o servidor de sessão por TCP (porta 8081, rede interna) |
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

## Rodar a API ou o servidor de sessão fora do Docker

Com o Compose rodando (para o MongoDB), pare o serviço correspondente e rode localmente:

```bash
docker compose stop api
./mvnw -pl api -am package -DskipTests                               # na raiz do repositório
JWT_SECRET=<mesmo do .env> java -jar api/target/api.jar
```

```bash
docker compose stop sessao ponte
./mvnw -pl ponte -am package -DskipTests                                  # gera sessao/target e ponte/target
JWT_SECRET=<mesmo do .env> java -jar sessao/target/sessao.jar 8081       # servidor de sessão (TCP), MongoDB em localhost:27017
SESSAO_HOST=localhost java -jar ponte/target/ponte.jar 8082              # ponte (WebSocket), ligada ao servidor local
```

O Nginx continua apontando para os nomes dos serviços no Docker; para testar pelo Nginx com o
processo local, acesse a API direto em `http://localhost:8080` e a ponte em `ws://localhost:8082`.

## Tolerância a falha

```bash
docker compose up -d --scale api=2 --scale sessao=2 --scale ponte=2   # duas réplicas de cada atrás do Nginx
docker compose stop mongo1                  # força nova eleição; mongo2 ou mongo3 assume
docker compose start mongo1
```

O Nginx resolve os nomes dos serviços dinamicamente (`server ... resolve`), então réplicas novas
ou derrubadas entram e saem do balanceamento sem reiniciar o Nginx.

### Cenários executados

Os resultados abaixo foram medidos com o servidor WebSocket anterior, antes da ponte. Ainda não foram
repetidos com a ponte: é preciso rodar o script de novo.

Script: `node infra/teste-tolerancia.mjs <sessao|api|mongo>` (Node 22+, Docker CLI). Ele cria uma
turma, 20 alunos e uma sessão `CLASSICA` próprios em cada execução, abre WebSocket pelo Nginx e
marca presenças com `presenca.marcar` (até 4 mensagens por segundo, abaixo do limite de 60 em 10 s).
Os containers são parados com `docker stop -t 10` (SIGTERM) e religados depois.

Ambiente: Docker Desktop no macOS, Compose v5.1.4, `docker compose up -d --build` e depois
`docker compose up -d --scale api=2 --scale sessao=2`. Primário inicial: `mongo1`.

**Servidor de sessão (réplica parada durante a chamada).** Execução de referência:

| Medida | Resultado |
|---|---|
| Tempo de `docker stop` da réplica | 0,6 s a 2,3 s |
| Clientes conectados à réplica parada | 11 (professor e 10 alunos), todos receberam `1001` ("Servidor reiniciando") |
| Clientes que não reconectaram | 0 de 21 |
| Reconexão (fechamento até `auth.ok`) | 70 a 96 ms |
| Presenças confirmadas durante a parada | 151 de 152; 1 falha `sem conexão` enquanto o professor reconectava |
| Maior intervalo sem confirmação | 521 ms |

Os alunos conectados à outra réplica não foram afetados. Confirmado com o servidor anterior: ele
fechava com `1001` no SIGTERM. Com a ponte, o `1001` vem da ponte: o servidor envia
`ComunicadoDeDesligamento` e a ponte fecha o WebSocket (`docs/servidor-modelo-socket.md`, seção 6.4).

Uma execução anterior ficou com o laço do professor parado: só 15 marcações em 40 s, nenhuma
confirmada depois da parada. O mesmo cenário, repetido, funcionou como descrito acima. Não encontrei
a causa dessa execução. Ela é tratada como inconclusiva.

Na primeira rodada, antes de cadenciar o laço, o professor enviou mensagens acima do limite do
servidor e foi fechado com `4008`. Esse resultado é do cliente de teste, não do protocolo, e não
entra na tabela.

**API (réplica parada durante leituras REST).** Leituras de `GET /api/auth/me` a cada 100 ms,
com 5 alunos conectados ao WebSocket:

| Medida | Resultado |
|---|---|
| Tempo de `docker stop` da réplica | 0,7 s |
| Leituras | 161 de 163 com HTTP 200 |
| Falhas | 2 `TimeoutError` do cliente (5 s), em +0,8 s e +6,0 s após a parada |
| Fechamentos de WebSocket dos alunos | 0 |

Na execução anterior, 220 de 221 leituras deram certo. O balanceamento do Nginx encaminhou a
maioria das requisições para a réplica viva, mas algumas requisições ficaram penduradas até o
timeout do cliente, em vez de falharem rápido e repetirem na outra réplica. Não investiguei a
configuração do `proxy_next_upstream`. É um ponto de atenção.

**MongoDB (primário parado durante marcações de presença).**

| Medida | Resultado |
|---|---|
| Primário antes e depois | `mongo1` → `mongo2` |
| Tempo de `docker stop mongo1` | 10,3 s (o período de tolerância de 10 s foi usado por completo) |
| Primeira confirmação depois da parada | 193 ms |
| Marcações confirmadas | 152 de 152, sem falhas |
| Alunos com presença confirmada | 20 |
| Presenças gravadas no banco depois da eleição | 20 |
| Presenças confirmadas perdidas | **0** |

A parada usou o tempo de tolerância inteiro, o que sugere que o `mongod` não encerrou em 10 s
com SIGTERM. Não verifiquei a causa. O cenário parou o primário de forma gracioso. Uma queda
abrupta (`docker kill`) não foi testada.

**Limitações deste teste.** Só paradas graciosas (SIGTERM) foram executadas. Um processo morto
com `kill -9` não foi testado. As execuções usaram um único professor e uma sessão `CLASSICA`. O
caminho do check-in por QR Code não foi exercitado sob falha. O script cria usuários de teste no
banco de desenvolvimento (`tol<sufixo>N@demo.br`).

## Limpar

```bash
docker compose down        # mantém os dados
docker compose down -v     # apaga também os volumes do MongoDB
```
