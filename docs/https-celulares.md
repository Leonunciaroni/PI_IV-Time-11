# HTTPS para testes com celulares (#40)

O perfil `https` do Docker Compose abre um Cloudflare Quick Tunnel até o Nginx.
Ele gera um endereço HTTPS temporário com certificado reconhecido pelo navegador,
sem conta Cloudflare, domínio próprio ou instalação de certificado nos celulares.
Professor, aluno, API e WebSocket passam pela mesma origem. O acesso local por HTTP
continua disponível na porta configurada em `NGINX_PORTA` (8088 por padrão).

É necessário Docker com Compose, o Docker Desktop iniciado no Windows e acesso à
Internet no computador e nos celulares. Os celulares podem estar no mesmo Wi-Fi do
computador; o tráfego passa pelo túnel, sem abrir portas no roteador. O endereço é
público enquanto o túnel estiver ligado: use contas e dados de demonstração e
compartilhe o link apenas com a equipe. O túnel serve para testes funcionais,
não para o teste de carga das 15 salas.

## Iniciar

Na raiz do repositório, em PowerShell:

```powershell
cd infra
# Preserva o arquivo caso o ambiente já esteja configurado.
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

Se for a primeira execução, gere um segredo e substitua o valor de `JWT_SECRET`
em `infra/.env` pelo resultado (não versione esse arquivo):

```powershell
node -e "console.log(require('node:crypto').randomBytes(48).toString('base64'))"
```

No Linux/macOS, copie `.env.example` para `.env` se ainda não existir e ajuste o
segredo da mesma forma. Os próximos comandos são iguais nos dois ambientes:

```sh
docker compose --profile https config --quiet
docker compose --profile https up -d --build
docker compose ps
docker compose logs -f tunel
```

Espere os serviços iniciarem e copie dos logs o endereço parecido com
`https://palavras-aleatorias.trycloudflare.com`. `Ctrl+C` encerra apenas o
acompanhamento dos logs; os contêineres continuam rodando. Abra o endereço no
Safari ou Chrome do celular, usando a URL inteira em HTTPS.

| Caminho no endereço HTTPS gerado | Uso |
|---|---|
| `/teste-celular/` | Diagnóstico de contexto seguro, câmera, microfone e localização |
| `/professor` | Aplicação do professor |
| `/aluno` | Aplicação do aluno |
| `/api/...` | API usada pelos apps na mesma origem |
| `/ws` | WebSocket, acessado pelos apps como `wss://` |
| `/nginx-health` | Retorna `ok` se o Nginx estiver acessível |

Não use o IP do computador em HTTP ou `localhost` no celular. `localhost` aponta
para o próprio aparelho. Use os apps servidos pelo Compose: no modo `npm run dev`,
o cliente WebSocket tem outra configuração de endereço. Não é necessário alterar
`API_URL`, `NEXT_PUBLIC_WS_URL` ou o `basePath` para o túnel do Compose.

## Validar no celular

1. Abra `/nginx-health` e confira a resposta `ok`.
2. Abra `/teste-celular/` e confira a mensagem **Contexto seguro disponível**.
3. Toque em **Testar câmera**, permita o acesso e confira a imagem.
4. Toque em **Testar microfone**, permita o acesso e confira o estado **Microfone
   liberado e ativo** e o indicador do aparelho. O teste apenas abre a captura;
   ele não mede o sinal nem valida a decodificação do modem.
5. Toque em **Testar localização** e confira a precisão informada. As coordenadas
   não são exibidas nem enviadas ao servidor.
6. Toque em **Parar câmera e microfone** e confira se os indicadores desligam.
7. Abra `/professor` e `/aluno`, faça login com contas de teste e abra uma chamada.
   No professor, confira **Conectado ao servidor de chamada**, confirmando o uso
   de WebSocket pelo túnel. Teste as permissões também nas telas que já usam
   câmera, microfone ou localização.

O diagnóstico não precisa de login e não depende de as telas do modo sonoro
estarem prontas. Capturas são desligadas ao parar, sair da página ou ocultar a aba.
Quando o domínio do túnel mudar, o navegador poderá pedir as permissões novamente;
os logins também ficam separados por origem.

Registre a validação na issue #40; preencha apenas depois de testar no aparelho:

| Data | Modelo / sistema | Navegador | HTTPS | Câmera | Microfone | Localização | Apps / WebSocket |
|---|---|---|---|---|---|---|---|
| Pendente | — | — | — | — | — | — | — |

## Parar ou gerar outro endereço

Na pasta `infra`:

```sh
docker compose stop tunel
```

Isso encerra o acesso público; os apps locais e os dados continuam disponíveis.
Para abrir outro túnel e consultar o novo endereço:

```sh
docker compose --profile https up -d tunel
docker compose logs --since 1m -f tunel
```

O hostname pode mudar a cada inicialização. Para parar todo o ambiente mantendo os
volumes do MongoDB, use `docker compose --profile https down`.

## Problemas comuns

| Sintoma | Como verificar |
|---|---|
| `docker` não reconhecido ou daemon indisponível | Instale/inicie o Docker Desktop e confirme `docker version` e `docker compose version`. |
| Túnel não gera URL ou não conecta | Veja `docker compose logs tunel`; o serviço precisa acessar a Internet, inclusive conexões de saída TCP na porta 7844 (HTTP/2). |
| URL retorna 502 | Confira `docker compose ps` e `docker compose logs nginx api sessao professor aluno`; o primeiro build pode demorar. |
| URL deixou de funcionar | Confirme que o túnel está rodando e use o endereço da inicialização mais recente. |
| Permissão negada | Libere a permissão do site no navegador e a permissão do navegador no Android/iOS. Abra em Safari/Chrome, fora do navegador interno de outro app. |
| Câmera ou microfone ocupado | Pare o diagnóstico antes de abrir o app e feche outros aplicativos que estejam usando o dispositivo. |
| Professor não conecta ao WebSocket | Use `/professor` no mesmo domínio HTTPS do túnel e confira se `sessao` está rodando. |

## Referências

- [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
- [Conexões WebSocket na Cloudflare](https://developers.cloudflare.com/network/websockets/)
- [Parâmetros do túnel: protocolo HTTP/2](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/run-parameters/)
- [Perfis do Docker Compose](https://docs.docker.com/compose/how-tos/profiles/)
