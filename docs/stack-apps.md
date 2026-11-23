<!-- Contribuição: Diogo Gonçalves Tonhosolo | RA: 25894007 | orientações de HTTPS para celulares -->
# Stack das aplicações (professor e aluno)

Decisão da Semana 1 (frentes C e D). Base: seção 3.2.3 do Relatório de Ideação.

## Decisão

| Item | Escolha | Motivo |
|---|---|---|
| Framework | **Next.js 16** (App Router) com **React 19** | Stack já dominada pela equipe; roda no navegador do celular sem instalação. |
| Linguagem | **TypeScript** (modo `strict`) | Tipos do protocolo (`docs/protocolo-v1.md`) espelhados no front, reduzindo erros de integração com o servidor Java. |
| Estilo | **Tailwind CSS 4** | Prototipação rápida, consistente com os wireframes de baixa fidelidade. |
| Organização | **Duas aplicações independentes**: `apps/professor` e `apps/aluno` | Cada frente evolui e faz deploy sem afetar a outra; o aluno não carrega código do painel do professor. |
| Renderização | Páginas de interação como **Client Components** | Câmera, microfone, WebSocket e `localStorage` só existem no navegador. |
| Comunicação | `fetch` para a API REST (`/api/...`) e `WebSocket` nativo para o servidor de sessão (`/ws`) | Sem dependências extras; o Nginx expõe ambos na mesma origem, sem CORS. |
| Sessão do usuário | JWT retornado pelo `POST /api/auth/login`, guardado em `localStorage` | O mesmo token autentica a API (header `Authorization`) e o WebSocket (mensagem `auth`). |
| QR Code (professor) | Biblioteca de geração de QR (ex.: `qrcode`) — Semana 5 | Gera o QR no cliente a cada janela TOTP, sem round-trip. |
| Leitura de QR (aluno) | `BarcodeDetector` nativo com fallback para biblioteca JS — Semana 5 | Evita dependência pesada nos navegadores que já suportam a API. |
| Áudio | Web Audio API (`OscillatorNode`, `AnalyserNode`) — spike na Semana 2 | Emissão e captação de 18–20 kHz sem plugins. |
| Gerenciador de pacotes | npm | Já instalado com o Node.js 24 LTS usado pela equipe. |

## Endereços

| Aplicação | Porta de desenvolvimento | Caminho no Nginx |
|---|---|---|
| Professor | 3000 | `/professor` |
| Aluno | 3001 | `/aluno` |

Cada aplicação usa `basePath` igual ao seu caminho no Nginx, para que ambas e a API compartilhem a
mesma origem (`http://<host>:8088`).

## Restrições do navegador

- **Contexto seguro:** câmera, microfone e geolocalização só funcionam em `https://` ou em
  `localhost`. Para testar em celulares na rede local é necessário HTTPS (certificado local ou
  túnel). O perfil `https` do Compose fornece um túnel temporário e uma página de
  diagnóstico; veja [HTTPS para celulares](https-celulares.md).
- **Áudio:** navegadores só liberam o `AudioContext` após um gesto do usuário (toque em botão).
  As telas de emissão e captação começam com um botão explícito.
- **Microfone no iOS:** o Safari aplica filtros de voz por padrão; a captação deve pedir
  `echoCancellation`, `noiseSuppression` e `autoGainControl` desligados.

## Estrutura de pastas (cada app)

```
apps/<app>/
├── src/
│   ├── app/            # rotas (App Router)
│   ├── components/     # componentes de interface
│   └── lib/            # cliente da API, cliente WebSocket, tipos do protocolo
├── public/
└── package.json
```
