# Checklist de entrega — issue #75

**Repositório:** [Leonunciaroni/PI_IV-Time-11](https://github.com/Leonunciaroni/PI_IV-Time-11)<br>
**Branch de trabalho:** `feature/teste-instalacao-zero`<br>
**Base:** `develop` em `9946a94`<br>
**Tag proposta:** `semana-7` — criar após a integração desta entrega em `develop`.

| Item | Estado | Evidência |
|---|---|---|
| Repositório | Pronto | Código e documentação em [GitHub](https://github.com/Leonunciaroni/PI_IV-Time-11) |
| README de instalação | Pronto na branch | Passo inicial de clone documentado em [`README.md`](../../README.md) |
| Relatório técnico | Preparado | [Markdown](relatorio-tecnico.md) e [PDF](relatorio-tecnico.pdf) |
| Apresentação | Preparada | [HTML editável](apresentacao-final.html) e [PDF](apresentacao-final.pdf) |
| Clone e Compose | Verificado neste notebook | Clone da `develop`, `.env` e volumes isolados; Docker Desktop e cache de imagens já existiam |
| Máquina completamente limpa | Pendente | Requer validação em VM ou computador sem o ambiente local, se este critério for exigido literalmente |
| PR do README e dos materiais | Pendente | Abrir e integrar a branch `feature/teste-instalacao-zero` em `develop` |
| Tag | Pendente | `semana-7`, após a integração em `develop` |
| Resultado na issue | Em andamento | [Issue #75](https://github.com/Leonunciaroni/PI_IV-Time-11/issues/75) |

## Resultado do teste local

Em 22/11/2026, o clone da `develop` (`9946a94`) subiu num projeto Compose separado. As cinco imagens de aplicação foram construídas; MongoDB, API, sessão e ponte ficaram saudáveis. O script de contas de demonstração concluiu e `/nginx-health`, `/professor` e `/aluno` responderam HTTP 200. O teste usou o mesmo notebook e imagens de build já existentes; não deve ser descrito como teste numa máquina nova.

## Pendências de aprovação

As medições de alcance do som e o teste de carga permanecem em suas respectivas issues. Este checklist de instalação não as substitui. A tag `semana-7` só deve apontar para o commit integrado em `develop`.
