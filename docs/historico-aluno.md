<!--
Guilherme Preventi Correia
RA: 25001694
-->

# Histórico de frequência do aluno — issue #32

A tela A4 está em `/aluno/historico`, protegida pela sessão do aluno. O botão “Ver histórico”
de cada turma na A2 abre essa página com `turmaId` na URL. Sem parâmetro, seleciona a primeira
turma matriculada. O seletor permite alternar entre as próprias turmas, sem usar um `alunoId`
fornecido na URL: a identidade vem da sessão autenticada.

O histórico usa `GET /api/turmas/{id}/frequencia/{alunoId}` já existente na API. Exibe a disciplina,
a turma, o percentual acumulado, presenças, aulas encerradas, aviso abaixo do mínimo e uma tabela
com todas as aulas, da mais recente à mais antiga. Cada linha tem data/horário, modo (clássica,
QR Code ou onda sonora) e status (presente/ausente), sem depender somente de cores.

Sem aulas encerradas, apresenta “—” na frequência e explica que ainda não há histórico. Sem
matrículas, mostra um estado vazio. Um id de turma que não está na lista do aluno é recusado antes
da consulta do histórico. Erros têm botão para tentar novamente; `401` encerra a sessão local e
leva ao login. Pedidos pendentes são cancelados ao mudar a turma ou sair da rota.

O resumo fica num bloco roxo, a cor da logo do aluno. Abaixo do mínimo, o bloco fica laranja e
mostra o aviso “Cuidado: sua frequência está abaixo do mínimo de 75%.” em vermelho. Em dia, o bloco
continua roxo e mostra o selo “Frequência em dia (mínimo de 75%).” em verde. Sem aulas encerradas,
não há selo. Os textos dizem o mesmo que as cores.

O visual segue o do app do professor, com tema claro e escuro. A tabela se adapta ao celular,
mantém cabeçalhos semânticos e não exige rolagem horizontal a 390 px.

## Validação

- `npm run lint` e `npm run build` em `apps/aluno`.
- Navegador com respostas controladas: acesso autenticado, navegação A2 → A4, troca de turma,
  percentual, datas em ordem, modos, presentes/ausentes, sem aulas, sem matrículas, turma inválida,
  erros com nova tentativa, expiração e tamanho de celular.
- As permissões e o cálculo do histórico continuam cobertos pelos testes existentes da API.

## Branch e dependência

`feature/aluno-historico-frequencia` parte de `feature/tela-aluno-turmas-chamada-aberta` (#25),
pois usa a lista de turmas e a área autenticada já implementadas. Integre a #25 antes da #32.
O cadastro do aluno permanece ausente, conforme a decisão do responsável pelo projeto.
