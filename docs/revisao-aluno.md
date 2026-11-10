# Revisão das telas do aluno — issue #77

Revisão do início A2, check-in A3, resultado A3b e histórico A4 contra
[os wireframes](wireframes.md). Mantém o visual do cadastro docente: roxo,
azul escuro, fundo claro e cartões brancos. Não introduz outro canal de presença.

## Ajustes

- A marca do cabeçalho volta ao início, com nome acessível e foco visível.
- O resultado e os erros do QR oferecem acesso ao histórico da turma. É possível
  conferir o registro quando um envio ficou sem confirmação, sem repetir o check-in
  automaticamente. O histórico considera apenas as aulas já encerradas.
- A tela de som informa que a confirmação ainda não está disponível e orienta pedir
  QR ou chamada clássica. Não pede microfone nem simula escuta, envio ou sucesso.

## Roteiro de regressão

Com os serviços rodando e Ana matriculada em uma turma:

| Tela / cenário | Conferência |
|---|---|
| A2, sem matrículas | Estado vazio com orientação para procurar o professor. |
| A2, turmas e chamada | Disciplina, modo, percentual e atalho para o canal da sessão correta. Falha de atualização não permite confirmar usando dados antigos. |
| A2, falha e nova tentativa | Mensagem em português; Atualizar recupera a lista sem recarregar a aplicação inteira. |
| A3 QR | Localizando → leitura da câmera → envio → resultado. Uma leitura gera no máximo um envio. |
| A3 QR, permissões | Câmera ou GPS negados exibem orientação; não enviam presença. Cancelar libera as tracks de vídeo. |
| A3 QR, recusas | Duplicidade, fora do raio e QR de outra sessão têm mensagens distintas. Não mostram sucesso e não repetem o envio automaticamente. |
| A3b | Sucesso só depois de `checkin.ok`, com horário devolvido pelo servidor, retorno ao início e acesso ao histórico. |
| A3 som | Mensagem de indisponibilidade, alternativa QR/clássica e nenhuma alegação de presença confirmada. |
| A4 | Seleção da própria turma, percentual, datas, modo e Presente/Ausente em texto. Turma inválida é recusada. |
| Sessão expirada | `401` limpa a sessão local e leva ao login. |
| Navegação | Marca do cabeçalho volta ao início; início abre histórico; resultado permite conferir o histórico da turma. |
| Responsividade | Sem rolagem horizontal da página em 390 px e 1280 px. |

## Evidência e limites

Os testes Vitest cobrem leitura QR, GPS e transporte. Executar `npm ci`, `npm test`,
`npm run lint` e `npm run build` em `apps/aluno`.

Na verificação de navegador, câmera virtual fornece quadros reais do gerador do
professor; a localização é controlada. API, ponte WebSocket/TCP e MongoDB são reais,
em banco de teste separado. Sucesso e recusas são conferidos com esses serviços;
as presenças também são conferidas no histórico após encerrar a chamada.
Falhas de carga, ausência de matrículas, canal sonoro indisponível e expiração
usam respostas controladas para testar a interface. Isso não substitui testes de
alcance ou permissões em um celular físico.

Não foram fornecidos relatos de testes em sala para reproduzir nesta revisão.
A validação física da câmera, GPS e navegação fica para a hospedagem com HTTPS,
conforme decisão do responsável. A captura por som (#45) aguarda o modem (#26);
o feedback de escuta e resultado desse canal só pode ser validado depois da integração.

A tela inicial (#25) e o histórico (#32) já foram integrados à `develop` pelos PRs
#102 e #103. Esta revisão valida sua convivência com o QR, sem recriar essas telas.
