# Edição de perfil — issue #69

Os dois apps oferecem `/professor/perfil` e `/aluno/perfil`, com acesso pelo cabeçalho
e proteção pela sessão do respectivo app. O visual usa fundo claro, cartões brancos,
roxo e azul escuro, como o cadastro docente.

Nome e e-mail são salvos por `PATCH /api/auth/me`. A troca de senha fica em outro
formulário e exige senha atual, nova senha e confirmação. Não se envia papel ou matrícula.
O servidor continua responsável por autenticação, unicidade e validação da senha atual.
Os erros de validação e de e-mail em uso aparecem em português.

A resposta é um `Usuario`, sem renovação de JWT. O store da sessão preserva token e
validade e notifica o cabeçalho imediatamente. Respostas de uma sessão anterior não
sobrescrevem um login recente nem recriam uma sessão encerrada. As senhas não vão para
o armazenamento do navegador e o formulário é limpo depois da troca e ao sair da rota.

Validação: testes Vitest dos contratos, dos limites Unicode/BCrypt e das respostas
atrasadas; lint e build dos dois apps. No navegador, conferir em cada papel o conflito
de e-mail, atualização do cabeçalho, persistência após recarga, recusa da senha atual
incorreta e login com a nova senha depois da troca.
