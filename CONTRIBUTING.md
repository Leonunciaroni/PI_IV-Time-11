# Como contribuir

## Branches

O repositório segue um modelo Git Flow simplificado:

| Branch | Função |
|---|---|
| `main` | Versão estável. Recebe merge de `develop` ao fim de cada marco semanal do cronograma. |
| `develop` | Integração contínua das frentes. Toda feature parte daqui e volta para cá. |
| `feature/<descricao>` | Nova funcionalidade (ex.: `feature/auth-jwt`). |
| `fix/<descricao>` | Correção de bug. |
| `docs/<descricao>` | Documentação (protocolo, arquitetura, wireframes). |
| `chore/<descricao>` | Configuração, build e infraestrutura do repositório. |
| `spike/<descricao>` | Experimento técnico com prazo fixo (ex.: `spike/audio-ultrassom`). |

Regras:

- Nunca faça commit direto em `main` ou `develop`.
- Crie a branch a partir de `develop` atualizada: `git switch develop && git pull && git switch -c feature/minha-feature`.
- Integre via pull request para `develop`, com merge `--no-ff` para preservar o histórico da feature.
- O merge de `develop` em `main` acontece no fechamento de cada semana (domingo) e recebe uma tag `semana-N`.

## Commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/pt-br/), com a descrição em português:

```
<tipo>(<escopo>): <descrição no imperativo>
```

Tipos: `feat`, `fix`, `docs`, `chore`, `build`, `test`, `refactor` e `spike` (experimentos).
Escopos de código: `api`, `sessao`, `common`, `professor`, `aluno`, `infra`, `audio`.
Escopos de documentação: `protocolo`, `dados`, `api`, `apps`, `wireframes`.

Exemplos:

```
feat(api): adicionar login com BCrypt e JWT
build(infra): subir MongoDB em replica set no Docker Compose
docs(protocolo): definir mensagens da versão 1
```

## Antes de abrir o pull request

```bash
./mvnw verify                        # common, api e sessao (os testes da api usam Docker)
cd apps/professor && npm run lint && npm run build
cd apps/aluno && npm run lint && npm run build
```

## Rodando os testes da API

Os testes de integração da API sobem um MongoDB descartável (`mongo:8.0`) em contêiner, com o
Testcontainers. Por isso o **Docker precisa estar rodando** (no Windows e no Mac, abra o Docker
Desktop antes). Na primeira execução as imagens são baixadas, o que demora um pouco.

```bash
./mvnw -pl api -am test                                 # todos os testes da api (e do common)
./mvnw -pl api -am test -Dtest=TurmaControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false               # uma classe só
```

Se algo falhar:

- **Testes não encontram o Docker:** confirme que `docker version` funciona no mesmo terminal. No
  Windows, adicione ao `Path` a pasta do Docker Desktop
  (`%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin`) e abra um terminal novo.
- **`./mvnw` falha ao baixar plugins com `PKIX path building failed`** (comum no Windows, em redes
  com antivírus ou proxy que trocam o certificado): faça o Java confiar nos certificados do sistema
  com a variável de ambiente `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`. Ela vale só
  nessa máquina e não deve ser colocada no repositório, porque `Windows-ROOT` não existe em Linux
  e Mac.

## Reuniões e acompanhamento

- Alinhamento toda quarta-feira, 30 minutos: o que foi feito, o que vem a seguir e o que está bloqueado.
- As decisões ficam registradas na issue da semana no GitHub.
