<!--
Guilherme Preventi Correia
RA: 25001694
-->

# Tela inicial do aluno — issue #25

Rota autenticada `/aluno`, conforme o wireframe A2. A página segue as cores e formas do
cadastro docente: roxo `#5040e8`, azul escuro `#172033`, fundo claro, bordas suaves e cartões
arredondados. Em celular, os cartões ficam em uma coluna; no desktop, em duas.

## Comportamento

- Carrega as próprias turmas de `GET /api/aluno/turmas`, usando o JWT do login.
- Mostra disciplina, turma, horário, frequência e alerta abaixo do mínimo de 75%.
- Sem aulas encerradas, mostra “—”, em vez de atribuir uma ausência ao aluno.
- Destaca todas as chamadas abertas das turmas do aluno e o modo escolhido pelo professor.
- QR Code e som levam a `/aluno/checkin?turmaId=...&sessaoId=...&modo=QRCODE|SOM`.
- Chamada clássica informa que a presença é registrada pelo professor, sem botão de autoconfirmação.
- A entrada de check-in verifica novamente a matrícula, o id da chamada e o modo. Se a aula
  encerrou ou mudou de canal, orienta voltar ao início e atualizar.
- “Ver histórico” abre `/aluno/historico?turmaId=...`, com o percentual e todas as aulas encerradas.
- Atualiza a cada 20 segundos enquanto a aba está visível, ao retornar à aba ou pelo botão Atualizar.
- Cancela pedidos pendentes ao sair da rota; não mostra dados de outra sessão após novo login.
- Erros permitem tentar novamente. Em atualização malsucedida, identifica os dados antigos e
  retira os atalhos de confirmação até uma consulta bem-sucedida.
- Um `401` encerra a sessão local e retorna ao login. Sair mantém o comportamento anterior.
- A área do aluno continua sem tela de cadastro.

## Limite da entrega

O canal QR Code da A3 agora usa a câmera, obtém a localização e envia `checkin` pelo WebSocket,
conforme [checkin-qr.md](checkin-qr.md). O sucesso só aparece após a confirmação do servidor.
O canal de áudio ainda depende da implementação da captura/decodificação do microfone; acessar
sua página não registra presença. A presença da chamada clássica continua sendo marcada pelo professor.

## Validação

- `./mvnw verify`: suíte completa de common, API e sessão com MongoDB descartável.
- `InicioAlunoControllerTest`: 401/403, isolamento entre matrículas, ordenação, frequência,
  lista vazia, modos QR/som/clássica, encerramento e ausência de segredo TOTP no JSON.
- `npm run lint` e `npm run build` em `apps/aluno`.
- Navegador com respostas controladas: login obrigatório, turmas e histórico, canais distintos,
  chamada encerrada, estados vazios, erro 503 com recuperação, expiração 401, atualização ao
  voltar à aba e ausência de rolagem horizontal a 390 px, inclusive em tema escuro.

## Como testar

Com a infraestrutura e as contas de demonstração configuradas conforme o README:

1. Execute `docker compose up -d --build` em `infra` para incluir o novo endpoint.
2. Em `apps/aluno`, execute `npm ci` e `npm run dev`.
3. Abra `http://localhost:3001/aluno` e entre com uma conta de aluno matriculada.
4. Abra/encerre uma sessão de uma turma desse aluno pelo servidor de sessão. A2 atualiza
   automaticamente em até 20 segundos ou imediatamente pelo botão Atualizar.

As contas e matrículas vêm da API/MongoDB; a tela não usa dados de demonstração fixos.
