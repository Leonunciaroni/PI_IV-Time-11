<!--
Guilherme Preventi Correia
RA: 25001694
-->

# Roteiro da demonstração — issue #74

Roteiro de operação para uma apresentação de **8 minutos**, com até 1 minuto de
reserva. Não é registro de um ensaio realizado. Referências: [wireframes](wireframes.md),
[API](api-rest.md), [protocolo](protocolo-v1.md) e [ambiente](../infra/README.md).

## Pessoas e material

Distribuição proposta para o ensaio: Leonardo apresenta e explica o problema;
Guilherme opera o notebook do professor; Lucas opera o celular do aluno; Diogo
acompanha o cronômetro e a infraestrutura; João Pedro explica a validação e observa
o painel ao vivo. A equipe pode trocar os operadores sem mudar a sequência.

Usar notebook com navegador, projetor e celular com câmera/localização autorizadas.
O celular deve acessar a hospedagem **HTTPS**, sem túnel temporário. Notebook e
celular devem alcançar o mesmo ambiente e banco. Conferir GPS, horário dos aparelhos,
bateria, acesso à rede e legibilidade do QR antes de iniciar o cronômetro.

## Preparação antes da apresentação

1. Subir o ambiente seguindo o README e verificar `docker compose ps`.
2. Em ambiente de demonstração isolado, executar `./infra/usuarios-demo.sh URL_BASE`.
   O professor `professor@demo.br` precisa estar em `PROFESSORES_PERMITIDOS`.
3. As contas existentes não são alteradas pelo script: confirmar o login com a senha
   de demonstração antes do ensaio. Não usar essas contas em produção com dados reais.
4. Criar, se ainda não existirem, a turma **PI IV — Turma A**, disciplina
   **Engenharia de Software**, horário **Ter 19:00**, e matricular Ana e Bruno.
   Na apresentação, usar sempre essa turma. A segunda turma, **PI IV — Turma B**,
   disciplina **Estrutura de Dados**, fica como exemplo de seleção no histórico.
5. Em Salas, cadastrar **Sala da demonstração**, usando a localização real do local
   e um raio compatível com a sala (por exemplo, 50 m). Confirmar a posição do celular
   antes de abrir a aula por QR. Não usar coordenadas fictícias para o teste físico.
6. Deixar sessões anteriores encerradas. Cada demonstração cria outra aula e passa
   a afetar a frequência. Usar o conjunto ampliado da #71 quando ele estiver disponível;
   o script atual cria apenas contas, não 60 matrículas nem aulas antigas.
7. Guardar capturas do início, QR, resultado e histórico obtidas no ensaio do mesmo
   ambiente, como material de reserva. Não apresentar capturas como execução ao vivo.

| Conta | Papel | Matrícula do script | Senha local de demonstração |
|---|---|---|---|
| `professor@demo.br` | Professor | P0001 | `demo12345` |
| `ana@demo.br` | Aluno | 24001234 | `demo12345` |
| `bruno@demo.br` | Aluno | 24001777 | `demo12345` |

## Sequência cronometrada

| Tempo | Quem fala / opera | Ação e evidência esperada |
|---|---|---|
| 00:00–00:40 | Leonardo / Guilherme e Lucas | Apresentar o problema da chamada manual. Entrar no professor (P1 → P2) e na Ana (A1 → A2). Mostrar a turma e a frequência. |
| 00:40–02:00 | Leonardo / Guilherme | Abrir a turma e uma chamada **Clássica** (P3 → P5 → P6). Marcar todos e desmarcar Bruno, deixando Ana presente. Mostrar a contagem e encerrar. |
| 02:00–04:00 | João Pedro / Guilherme e Lucas | Abrir **QR Code**, selecionar a sala (P5 → P7). Na Ana, atualizar o início e escolher Confirmar presença (A2 → A3). Liberar localização e câmera, ler o QR e mostrar A3b com confirmação e horário. O professor confere Ana no painel. |
| 04:00–04:40 | João Pedro / Lucas e Guilherme | Tentar novamente a mesma presença e mostrar a recusa de duplicidade, sem aumentar o contador. Explicar matrícula, token, raio e dispositivo. Encerrar a chamada. |
| 04:40–05:50 | João Pedro / Guilherme e Lucas | **Bloco condicionado ao modem:** abrir Som (P8), tocar Iniciar emissão; na Ana iniciar escuta (A3), captar e conferir confirmação e painel. Encerrar. Se a dependência estiver pendente, usar a fala e o plano B abaixo. |
| 05:50–07:10 | Leonardo / Lucas e Guilherme | Abrir o histórico do aluno (A4), conferir cada aula, modo e presença. Mostrar o dashboard P9 quando a tela da #43 estiver integrada; conferir percentual e distribuição por modo. |
| 07:10–08:00 | Leonardo / Diogo | Retomar o ganho de tempo, as três formas previstas de chamada e os limites dos testes. Encerrar a demonstração dentro de 8 minutos. |

## Dependências que precisam ser conferidas no ensaio

Em 10/11/2026, a `develop` tem a validação do canal SOM e o módulo isolado de emissão,
mas o modem FSK (#26), a captação no aluno (#45) e a integração da emissão na tela
ainda estão pendentes. O botão Som está desabilitado. **Não anunciar o modo sonoro
como entregue nem habilitá-lo apenas para a apresentação.** Enquanto isso, no bloco
de som, explicar por até 30 s a dependência e demonstrar a alternativa por QR.

A tela P9 também depende da #43. Se não estiver integrada, mostrar o histórico A4
e o resumo da chamada; a API de estatísticas existente pode ser apresentada como
evidência técnica separada (`GET /api/turmas/{id}/estatisticas`, com autenticação),
sem chamá-la de dashboard pronto. O roteiro contempla a sequência final e identifica
o que ainda impede executar todos os blocos ao vivo.

## Plano B

| Falha | Ação do operador | Fala e limite |
|---|---|---|
| Som não captado em 15 s | Encerrar a sessão sonora e abrir uma nova em QR Code. | Explicar a dependência de hardware/alcance. Não trocar o modo de uma sessão existente nem reutilizar o token de outra. |
| Câmera, localização ou raio impedem o QR | Conferir permissões; se não resolver em 20 s, encerrar e abrir uma chamada clássica. | O professor registra a presença manualmente. Não desativar a validação de raio. |
| Falha da rede do celular | Diogo confere conectividade. Usar a clássica se o notebook continua conectado. | Se o servidor também ficar indisponível, usar as capturas do ensaio, identificando-as como gravação/reserva. Nenhuma tela deve afirmar presença sem confirmação do servidor. |
| WebSocket reconectando | Aguardar até 15 s e conferir o histórico antes de repetir o envio. | Se o envio anterior ficou sem resposta, não afirmar que falhou nem fazer repetição automática. |
| Tempo perto do limite | Pular a segunda turma e o exemplo de duplicidade. | Preservar uma chamada completa, o resultado no aluno e o histórico. |

Antes de executar o plano B, encerrar a sessão atual se o servidor estiver acessível:
só uma chamada pode ficar aberta por turma. Se toda a rede caiu, não prometer nova
marcação; usar o material de reserva e retomar após a recuperação.

## Registro do ensaio (a preencher pela equipe)

Data e ambiente: ______ · Operadores: ______ · Tempo total: ______

| Verificação física | Resultado / ocorrência |
|---|---|
| Login nos dois aparelhos e acesso HTTPS | A preencher |
| QR lido, GPS dentro do raio e presença ao vivo | A preencher |
| Som com o modem integrado, ou dependência declarada | A preencher |
| Histórico e dashboard, ou limitação declarada | A preencher |
| Plano B e tempo de recuperação | A preencher |
