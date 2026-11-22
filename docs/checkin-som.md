# Captação e check-in por som — issue #45

A rota `/aluno/checkin?turmaId=...&sessaoId=...&modo=SOM` usa o microfone para
receber o token FSK e confirmar a presença pelo protocolo v1. Mantém o visual do
cadastro docente e as telas A3/A3b dos [wireframes](wireframes.md).

## Fluxo

1. A entrada consulta as próprias turmas e `GET /api/sessoes/ativas`. Só abre o
   leitor se turma, sessão e modo SOM coincidirem com os parâmetros do link.
2. Os módulos de áudio são carregados antes de habilitar o botão. O toque em
   **Iniciar escuta** abre o `AudioContext` e pede microfone mono, com
   `echoCancellation`, `noiseSuppression` e `autoGainControl` desligados.
3. O receptor do modem aplica o filtro passa-altas e transporta PCM por
   AudioWorklet para o Worker. O decodificador só entrega quadros íntegros, com
   preâmbulo, versão, seis dígitos ASCII e CRC válidos. A saída de áudio é silenciosa.
4. O primeiro token válido encerra microfone, Worker e contexto antes da consulta
   seguinte. A tela verifica novamente a sessão ativa (prazo de 10 segundos).
5. Com a conexão autenticada pronta, envia uma única mensagem `checkin`:

   ```json
   { "sessaoId": "000000000000000000000045", "token": "001234",
     "canal": "SOM", "dispositivoId": "uuid-do-navegador" }
   ```

6. A confirmação só aparece depois da resposta `checkin.ok` correlacionada, com a
   mesma sessão e um horário válido. O servidor decide validade temporal,
   matrícula, modo e duplicidades. O professor recebe a presença no painel ao vivo.

O JWT vai no `auth` do WebSocket e no cabeçalho da API. O check-in sonoro não pede
GPS nem envia coordenadas, áudio ou gravação. A geração de `dispositivoId` usa o
mesmo armazenamento do login e do QR Code. Zeros à esquerda do token são preservados.

## Estados e interrupções

- A interface distingue abertura do microfone, escuta, envio, sucesso e erro.
- A abertura do microfone tem limite de 20 s; sem sinal íntegro, a escuta termina
  após 45 s e orienta aproximar-se ou pedir QR Code/chamada clássica.
- Cancelar, ocultar a aba ou sair da rota libera a captura. Uma permissão concedida
  depois do cancelamento é descartada. O retorno à tela exige novo toque.
- Perder a conexão interrompe a escuta. Um código não fica na fila para envio após
  reconectar, e o mesmo check-in não é reenviado automaticamente.
- Token inválido/expirado orienta **nova escuta**, sem instrução para ler QR. Sessão
  encerrada, modo incompatível, matrícula ausente, presença duplicada e aparelho já
  usado têm mensagens específicas. As recusas definitivas não oferecem reenvio.
- Perda de conexão durante o envio ou resposta incerta orienta conferir o histórico
  antes de tentar novamente. O histórico considera aulas já encerradas.

## Origem e empacotamento do modem

O receptor, codec, Worker e worklet em `apps/aluno/public/audio/` são cópias **sem
alterações** de `audio/` da branch `feature/modem-audio-fsk`, commit
[`b0246a6796230a242dec252fbffa87c195eb0536`](https://github.com/Leonunciaroni/PI_IV-Time-11/tree/b0246a6796230a242dec252fbffa87c195eb0536/audio)
(PR #124). O Docker do aluno usa `apps/aluno` como contexto; os quatro arquivos são
servidos juntos sob `/aluno/audio/`, preservando os imports relativos do Worker.

`src/lib/audio/origem.json` registra a origem e o SHA-256 de cada arquivo. Os testes
detectam alterações na cópia. Se o perfil do modem mudar após as medições da #13,
sincronizar esses arquivos e o adaptador do professor no mesmo release. O perfil
atual é o da equipe: 18.500/19.500 Hz, símbolos de 20 ms e CRC-16/IBM-3740.

## Executar e verificar

```sh
cd apps/aluno
npm ci
npm test
npm run lint
npm run build
```

Para o fluxo inteiro, integrar também a emissão do professor (PR #125, dependente
do #118). A captação parte do polimento do aluno (#116), que usa o QR/cliente de
sessão do #111. Os PRs permanecem separados; não é preciso alterar o contrato da API
ou do servidor de sessão.

1. Subir o Compose com `docker compose up -d --build` em `infra`.
2. Usar professor e aluno de demonstração, com o aluno matriculado na turma.
3. Professor: abrir uma chamada **Som** e tocar **Iniciar som**.
4. Aluno: abrir **Confirmar presença**, tocar **Iniciar escuta** e autorizar o
   microfone. Manter as duas páginas em primeiro plano.
5. Conferir confirmação e horário no aluno e a presença no painel do professor.
6. Encerrar a chamada e conferir a aula em modo Som no histórico do aluno.

Para testar em celular, usar a hospedagem com HTTPS. O teste físico de alcance e
audibilidade continua separado dos testes digitais.

## Evidência automatizada

Em 16/11/2026, passaram os testes do modem/emissor original (25), do aluno (60),
do professor na branch de emissão (120), lint/build dos dois apps e `mvnw verify`.
O build do aluno inclui os quatro módulos estáticos; a verificação de navegador
exercita os imports reais do Worker e do AudioWorklet.

No teste completo, a tela real do professor abre a sessão e gera o quadro FSK.
Esse PCM alimenta um microfone virtual do navegador do aluno, com API, ponte TCP e
MongoDB reais em um banco descartável. A presença aparece no painel e no histórico,
e o banco é conferido para ausência de campos de áudio e coordenadas.

Foram exercitados também token inválido/expirado, duplicidade, aparelho reutilizado,
microfone negado, navegador incompatível, cancelamento, permissão tardia, aba oculta,
saída da tela, módulo indisponível e sessão encerrada durante a captura. O prazo da
consulta da API tem teste próprio. Esses testes não comprovam transmissão física
a 3 m nem a resposta acústica dos microfones e alto-falantes da sala.
