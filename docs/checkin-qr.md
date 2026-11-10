# Leitura do QR Code e check-in com geolocalização — issue #39

## Integração existente

A branch `feature/Checkin-QR-Code` parte da `develop` com a geração do QR do professor (#38),
TOTP e antifraude já implementados no servidor. Integra também a tela inicial do aluno (#25)
para reutilizar o atalho de chamada aberta. O QR do professor não foi substituído ou alterado.

O leitor aceita exatamente `FA1:<sessaoId>:<token>`, emitido por
`apps/professor/src/lib/totp.ts:conteudoQr`. `sessaoId` é o ObjectId hexadecimal de 24 posições
e `token` tem seis dígitos, incluindo zeros à esquerda. QR inválido ou de outra chamada é
recusado antes do envio. Não há digitação manual do token nem upload da imagem.

## Fluxo A3 → A3b

1. O aluno entra na A2 e toca em **Confirmar presença** na chamada QR Code.
2. `/aluno/checkin?turmaId=...&sessaoId=...&modo=QRCODE` confere a turma, a chamada e o modo.
3. O WebSocket autentica com o JWT no envelope `auth`; o token de login não vai na URL.
4. **Iniciar leitura do QR Code** pede localização com alta precisão, `maximumAge: 0` e timeout
   de 10 segundos, e depois abre a câmera traseira preferencial. Não pede áudio.
5. Usa `BarcodeDetector` se ele suportar `qr_code`. Sem suporte, na falha de inicialização
   ou na detecção nativa, carrega `jsQR` sob demanda e processa os pixels localmente.
6. Ao ler, desliga a câmera. Se a localização tem mais de 15 segundos, pede uma nova posição.
7. Envia pelo WebSocket o envelope v1 `checkin` com `sessaoId`, token do QR, `canal: QRCODE`,
   `dispositivoId`, latitude, longitude e precisão em metros.
8. Só mostra **Presença confirmada** depois de `checkin.ok` correlacionado pelo `ref`, com
   o id esperado e o horário retornado pelo servidor. O servidor continua responsável por
   validar TOTP, raio, matrícula, duplicidade e dispositivo, e gravar no MongoDB.

O identificador do aparelho é o UUID já usado pelo app (`fa.aluno.dispositivoId`), mantido no
localStorage. Se o armazenamento está bloqueado, o check-in não é enviado. Imagem e token do QR
não são persistidos no navegador. Só o identificador do dispositivo e a sessão seguem as regras
de armazenamento já existentes.

## Falhas e ciclo de vida

- Mensagens próprias em português para todos os códigos de `CodigoErro`, incluindo token expirado,
  fora do raio, não matriculado, presença duplicada, aparelho já usado e limite de mensagens.
- Falhas de câmera, localização, contexto inseguro e conexão também orientam a próxima ação.
- Uma leitura gera no máximo um envio; falha exige nova ação do usuário e QR novo. A reconexão
  usa backoff e autentica novamente, sem reproduzir um check-in pendente.
- Resposta perdida ou timeout não significa que a gravação falhou: a mensagem orienta consultar
  o histórico antes de repetir. A tela não anuncia sucesso por conta própria.
- Sair da rota, ocultar a aba ou cancelar libera os tracks da câmera e ignora callbacks tardios.
  Ao retornar após interrupção, o usuário inicia uma nova leitura explicitamente.
- Cancelar a leitura está disponível antes do envio; durante o envio, o botão fica desabilitado.
- A precisão do GPS é mostrada em metros; o servidor, e não a interface, decide se está no raio.

## Como executar e testar

```powershell
cd infra
docker compose up -d --build
```

1. Com o professor, cadastre uma sala com coordenadas/raio corretos, matricule o aluno e abra
   uma chamada no modo QR Code. Mantenha o QR rotativo visível na tela do professor.
2. Entre como aluno em `http://localhost:8088/aluno`, selecione a chamada e inicie a leitura.
3. Autorize câmera e localização. Aponte o aparelho para o QR atual e aguarde a resposta.
4. Confira a presença no painel do professor e, após encerrar a aula, no histórico do aluno.

Em **celular**, a origem precisa ser HTTPS para câmera e GPS. `http://IP-DO-NOTEBOOK:8088`
não é uma origem segura; localhost é a exceção para testar no próprio computador.
Use uma origem HTTPS acessível pelos dois aparelhos, com proxy de `/api` e `/ws` como no Nginx.
Em `npm run dev` do aluno (porta 3001), `/api` vai ao Compose; o WebSocket usa por padrão
`ws://localhost:8088/ws`, como no professor. Para outro host, configure `NEXT_PUBLIC_WS_URL`
no ambiente de desenvolvimento/build (por exemplo `wss://seu-host/ws`).

## Verificação realizada

- `npm run test`, `npm run lint` e `npm run build` em `apps/aluno`.
- Testes unitários do leitor: matriz exportada do gerador real do professor, zeros à esquerda,
  formato/versão, outra sessão, detecção nativa, fallback e liberação de câmera com permissão tardia.
- Testes do transporte: autenticação antes do envio, correlação, tipo da resposta, erro traduzido,
  timeout, ping, expiração e reconexão sem reenvio automático.
- Testes de localização, contexto inseguro, cancelamento e todos os 17 códigos do protocolo.
- `./mvnw verify`: suíte completa de common, API e sessão, incluindo o check-in do servidor.
- Oito cenários de navegador usando API, WebSocket e MongoDB reais em um banco temporário:
  câmera virtual com QR produzido pelo gerador do professor e leitura real pelo jsQR, presença
  duplicada, fora do raio, caminho nativo simulado, QR de outra chamada, câmera negada, localização
  negada e cancelamento. Duas presenças foram conferidas no encerramento e no histórico da API.
- Tracks liberados ao concluir/cancelar e layout sem rolagem horizontal a 390 px.

Câmera e GPS físicos de celulares ainda precisam do teste em sala. A automação usou vídeo e
coordenadas simulados; isso não mede a qualidade óptica da câmera nem a precisão real do GPS.

O Next.js do aluno foi alinhado à versão 16.4.5 já usada pelo professor. A fixture do QR está no
próprio app do aluno, para preservar o build independente de seu Dockerfile.
