# Guia: rodar o Fast Attendance e testar os modos de chamada

Para quem for subir o sistema do zero e testar chamada **clássica**, por **QR Code** e por **som**,
com o professor no notebook e o aluno no celular. Os comandos vêm do [README](../README.md), do
[infra/README.md](../infra/README.md) e de [https-celulares.md](https-celulares.md); este guia só
os põe em ordem e diz o que fazer quando algo falha.

> Escrito a partir da documentação do repositório. Ainda não foi executado numa máquina limpa:
> se um passo não bater, corrija este arquivo.

## O que precisa existir

| Item | Para quê |
|---|---|
| Docker com Compose v2, **Docker Desktop aberto** (Windows e macOS) | Sobe MongoDB, API, servidor de sessão, ponte, apps e Nginx |
| Git (no Windows, o **Git Bash**) | Clonar e rodar os scripts `.sh` de contas e dados |
| Node.js 22 ou superior | Só para o pré-voo da rede (`infra/preflight-tunel.mjs`) |
| Um celular com câmera, microfone e localização | Aluno |
| Internet no notebook e no celular | Túnel HTTPS (ver "HTTPS no celular") |

## 1. Subir o ambiente

```bash
git clone https://github.com/Leonunciaroni/PI_IV-Time-11.git
cd PI_IV-Time-11
git checkout develop
cd infra
cp .env.example .env
```

Edite `infra/.env` e troque o `JWT_SECRET` por um valor aleatório de pelo menos 32 bytes
(`node -e "console.log(require('node:crypto').randomBytes(48).toString('base64'))"`). Deixe
`PROFESSORES_PERMITIDOS=professor@demo.br`. O `.env` não vai para o Git.

```bash
docker compose up -d --build
docker compose ps
```

O primeiro build baixa imagens e compila tudo, então demora. Espere `api`, `sessao` e `ponte`
aparecerem como **healthy** no `docker compose ps`.

## 2. Criar as contas de demonstração

Ainda em `infra`, no Git Bash:

```bash
./usuarios-demo.sh
```

| Conta | Papel | Senha |
|---|---|---|
| `professor@demo.br` | Professor | `demo12345` |
| `ana@demo.br` | Aluno | `demo12345` |
| `bruno@demo.br` | Aluno | `demo12345` |

O script é idempotente: contas que já existem são mantidas.

**Dados prontos (opcional).** `./dados-demo.sh` cria mais 60 alunos (`aluno01@demo.br` a
`aluno60@demo.br`, matrícula `24100001` a `24100060`) e também turmas, salas, aulas encerradas e
presenças, para o dashboard ter números. Ele já **matricula alunos nas turmas**. Para demonstrar a
importação de CSV, importe alunos que **ainda não estão** na turma, senão tudo aparece como "já
matriculado". Detalhes na seção "Dados de demonstração" de [infra/README.md](../infra/README.md).

Sem rodar o `dados-demo.sh`, crie pela tela do professor, antes de testar, uma turma, uma sala e a
matrícula de Ana e Bruno.

## 3. Abrir os apps

- **Professor, no notebook:** `http://localhost:8088/professor`. `localhost` já conta como contexto
  seguro, então não precisa de HTTPS.
- **Aluno, no celular:** precisa de **HTTPS** (câmera, microfone e localização só funcionam assim).
  Veja a próxima seção. Não use `http://IP-do-notebook:8088` para QR e som.

## 4. HTTPS no celular

O celular abre um endereço `https://...trycloudflare.com` que chega ao notebook por um túnel
temporário. O túnel precisa de saída de rede na **porta 7844**, e redes de faculdade e de empresa
costumam bloqueá-la. Por isso, **antes de tudo, no local**:

```bash
node infra/preflight-tunel.mjs      # na raiz do repositório
```

- **"esta rede deixa o túnel funcionar"** (código 0): siga adiante.
- **Timeout na 7844:** a rede bloqueia a porta. O túnel não vai conectar. Troque de rede (por
  exemplo, o hotspot do celular) e rode o pré-voo de novo.
- **EOF no handshake:** algum filtro corta a conexão. Mesmo caminho: outra rede.

Passou? Suba o túnel e pegue o endereço:

```bash
cd infra
docker compose --profile https up -d tunel
docker compose logs tunel
```

Procure nos logs a linha com `https://palavras-aleatorias.trycloudflare.com` e confirme
`Registered tunnel connection`. No celular, abra `<endereço>/teste-celular/` (diagnostica HTTPS,
câmera, microfone e localização) e depois `<endereço>/aluno`.

Se mudar de rede com o Docker aberto, **reinicie o Docker Desktop** antes de subir o túnel. A URL
é pública enquanto o túnel estiver ligado: use só contas de demonstração e não divulgue o link.
Para encerrar: `docker compose stop tunel`.

### Plano reserva: HTTPS local, sem internet

Se o pré-voo falhar em todas as redes disponíveis, o celular não precisa da internet: basta estar
na **mesma rede do notebook**, falando HTTPS com ele diretamente. O serviço `nginx-https` serve os
mesmos apps na porta **8443** com um certificado autoassinado. O notebook e o celular devem estar
na mesma rede **sem isolamento de clientes** (Wi-Fi de faculdade e de empresa costumam isolar). Por
exemplo, o hotspot de um **outro** celular (o do aluno não pode ser o ponto de acesso dele mesmo).

Com o notebook já na rede que o celular vai usar:

```bash
node infra/https-local/gerar-certificado.mjs      # na raiz; imprime os endereços para o celular
cd infra
docker compose --profile https-local up -d nginx-https
```

No celular, abra o endereço impresso (`https://IP-DO-NOTEBOOK:8443/aluno`, ou `/teste-celular/`
primeiro). O navegador avisa que a conexão não é segura, porque o certificado é nosso: aceite uma
vez (Chrome: Avançado → Continuar; Safari: Mostrar detalhes → acessar este site). Depois, câmera,
microfone e localização funcionam como no túnel.

- **Trocou de rede?** O IP muda: rode o `gerar-certificado.mjs` de novo e
  `docker compose --profile https-local restart nginx-https`.
- **O celular não carrega a página?** Confira se ele está na mesma rede (os IPs começam igual, por
  exemplo `192.168.68.`), se a rede não isola clientes e se o firewall do Windows deixa a entrada na
  porta 8443. O Docker Desktop costuma ter regra própria para redes Privada e Pública.
- O professor continua em `http://localhost:8088/professor` no notebook.
- Para encerrar: `docker compose --profile https-local stop nginx-https`.

Este caminho foi testado **só a partir do próprio notebook** (rotas, login e WebSocket seguro,
inclusive pelo IP da rede). Ainda **não** foi aberto num celular de verdade.

## 5. Testar cada modo

O professor abre a chamada no notebook; o aluno confirma no celular. **Só pode haver uma chamada
aberta por turma:** se aparecer "Já existe uma chamada aberta para esta turma", encerre a anterior.

| Modo | Professor | Aluno (celular) |
|---|---|---|
| **Clássica** | Abre a chamada e marca a presença de cada aluno | Vê a chamada aberta no início |
| **QR Code** | Escolhe a **sala** e abre a chamada. O QR gira a cada 15 s | **Confirmar presença** → Iniciar leitura do QR Code, autoriza câmera e localização |
| **Som** | Abre a chamada em Som e toca **Iniciar som** | **Confirmar presença** → **Iniciar escuta**, autoriza o microfone |

Cuidados:

- **QR e som dependem da sala.** O servidor valida se o celular está dentro do raio da sala. No local
  da apresentação, cadastre uma sala com a localização real e um raio compatível (por exemplo, 50 m),
  e não use coordenadas fictícias.
- **Som:** mantenha as duas páginas em primeiro plano, com o volume do notebook alto e o celular
  perto do alto-falante. O alcance e a resposta dos alto-falantes e microfones da sala não foram
  comprovados por testes automáticos ([checkin-som.md](checkin-som.md)).
- O aluno precisa estar **matriculado** na turma, senão a presença é recusada.
- Presença duplicada e aparelho já usado são recusados de propósito.

A sequência cronometrada de 8 minutos está em [roteiro-demonstracao.md](roteiro-demonstracao.md),
incluindo o plano B se um modo falhar. Esse roteiro é de 10/11 e ainda trata o modo Som como
pendente: o modo já existe na `develop`, então confira o que valer antes de usar.

## 6. Problemas comuns

| Sintoma | O que verificar |
|---|---|
| `docker` não encontrado ou daemon indisponível | Abra o Docker Desktop e confirme `docker version` |
| `JWT_SECRET` obrigatório ao subir | `infra/.env` não existe ou está sem `JWT_SECRET` |
| Porta 8088 ocupada | Mude `NGINX_PORTA` no `.env` (e use a nova porta em todos os endereços) |
| Login do professor ou do aluno falha | As contas não foram criadas: rode `./usuarios-demo.sh` |
| `./usuarios-demo.sh` diz que o professor não está permitido | `PROFESSORES_PERMITIDOS` no `.env` precisa ter `professor@demo.br` (depois, `docker compose up -d`) |
| URL do túnel responde `530` | O túnel não conectou à borda. Rode o pré-voo e veja `docker compose logs tunel`. Sem rede que passe no pré-voo, use o "Plano reserva" acima |
| `nginx-https` não sobe: "Sem certificado" | Rode `node infra/https-local/gerar-certificado.mjs` antes |
| Câmera ou microfone negados | Libere a permissão do site no navegador e do navegador no sistema. Abra no Safari ou Chrome, fora de navegador embutido de outro app |
| Check-in recusado: fora do raio | Confira a localização do celular e o raio da sala |
| Professor não mostra "Conectado ao servidor de chamada" | Abra o professor pelo mesmo domínio e confira `docker compose ps` (serviço `sessao` e `ponte`) |

## 7. Encerrar

```bash
docker compose --profile https down        # para tudo, mantém os dados do MongoDB
docker compose --profile https down -v     # para tudo e APAGA os dados (irreversível)
```
