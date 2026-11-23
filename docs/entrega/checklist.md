<!-- Preparação do material: Diogo Gonçalves Tonhosolo | RA: 25894007 -->
# Checklist de entrega — issue #75

**Repositório:** [Leonunciaroni/PI_IV-Time-11](https://github.com/Leonunciaroni/PI_IV-Time-11)<br>
**Branch de trabalho:** `feature/teste-instalacao-zero`<br>
**Base:** `develop` em `9946a94`<br>
**Tag de entrega:** `semana-7` — snapshot da integração desta entrega em `develop`.

| Item | Estado | Evidência |
|---|---|---|
| Repositório | Pronto | Código e documentação em [GitHub](https://github.com/Leonunciaroni/PI_IV-Time-11) |
| README de instalação | Integrado | Passo inicial de clone em [`README.md`](../../README.md), PR [#143](https://github.com/Leonunciaroni/PI_IV-Time-11/pull/143) |
| Relatório técnico | Preparado | [Markdown](relatorio-tecnico.md) e [PDF](relatorio-tecnico.pdf) |
| Apresentação | Preparada | [HTML editável](apresentacao-final.html) e [PDF](apresentacao-final.pdf) |
| Clone e Compose | Verificado neste notebook | Clone da `develop`, `.env` e volumes isolados; Docker Desktop e cache de imagens já existiam |
| Máquina completamente limpa | Verificado em clone isolado | Clone da `develop`, `.env` e volumes próprios; Docker Desktop e cache de imagens já existiam neste notebook |
| PR do README e dos materiais | Integrado | PR [#143](https://github.com/Leonunciaroni/PI_IV-Time-11/pull/143) em `develop` |
| Tag | Publicada | `semana-7` no snapshot integrado em `develop` |
| Resultado na issue | Registrado | [Issue #75](https://github.com/Leonunciaroni/PI_IV-Time-11/issues/75) |

## Resultado do teste local

Em 22/11/2026, o clone da `develop` (`9946a94`) subiu num projeto Compose separado. As cinco imagens de aplicação foram construídas; MongoDB, API, sessão e ponte ficaram saudáveis. O script de contas de demonstração concluiu e `/nginx-health`, `/professor` e `/aluno` responderam HTTP 200. O teste usou o mesmo notebook e imagens de build já existentes; não deve ser descrito como teste numa máquina nova.

## Pendências de aprovação

As medições de alcance do som e o teste de carga permanecem em suas respectivas issues. Este checklist de instalação não as substitui. O teste de instalação foi feito em um clone e projeto Compose sem dados anteriores, no mesmo computador que já tinha Docker Desktop.
