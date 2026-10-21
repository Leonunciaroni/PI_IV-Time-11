# Cadastro de alunos e professores

Implementação da issue #67 nos dois apps Next.js, com TypeScript e Tailwind CSS.

## Acesso e separação

- Aluno: `/aluno/cadastro`, a partir do link na tela de login.
- Professor: `/professor/cadastro`, a partir do link na tela de login.
- Cada app envia seu papel fixo (`ALUNO` ou `PROFESSOR`) para `POST /api/auth/registro`.
- O cadastro do aluno é aberto. O do professor depende da lista `PROFESSORES_PERMITIDOS`,
  validada pela API. Ela aceita e-mails completos ou domínios iniciados por `@`; vazia,
  bloqueia todos os novos professores. A tela não recebe nem tenta reproduzir essa lista.
- O link entre apps usa `<a>`; os links internos usam `next/link`, respeitando o `basePath`.

## Campos e referência visual

As telas seguem a composição em duas colunas do protótipo fornecido: formulário em fundo branco,
botão roxo e painel azul escuro com benefícios. No celular as colunas ficam empilhadas, com o
formulário primeiro. A área escolhida fica explícita no título e permite acessar o outro cadastro.

Os campos seguem o contrato de [API REST](api-rest.md): nome (até 120 caracteres), e-mail (até
254), matrícula/registro funcional (até 30) e senha (8 a 72). Nome, e-mail e matrícula têm espaços
externos removidos antes do envio; a senha é preservada. Campos com apenas espaços são recusados.

Departamento foi deixado fora desta entrega por decisão confirmada pelo responsável pela issue,
pois não existe no contrato da API. O registro funcional ocupa o campo de matrícula do professor.
O aceite de termos do protótipo não foi incluído: o projeto ainda não fornece documentos de termos
e privacidade nem um contrato de persistência desse aceite.

## Resultado e erros

Durante o envio, os campos e o botão ficam desabilitados. O botão de senha alterna sua visibilidade.
As mensagens de erro do servidor aparecem em português; erros de campos são associados aos inputs
e usam textos em português definidos no app, inclusive quando a validação Java retorna mensagens
em inglês. O formulário mantém os dados para permitir correção e nova tentativa.

Após `201`, o formulário é limpo e a navegação segue para `/login?cadastro=sucesso`, dentro do
app correspondente. O login exibe uma confirmação. O cadastro não cria sessão nem armazena JWT.

## Verificação

Execute `npm run lint` e `npm run build` em cada app. Para validar com a API real, suba a
infraestrutura conforme o README e use `npm run dev` nos apps, que encaminham `/api` ao Compose.

| Cenário | Resultado esperado |
|---|---|
| Campos vazios ou apenas espaços | Não envia; aponta os campos a corrigir. |
| Senha com menos de 8 ou mais de 72 caracteres | Impede envio fora dos limites. |
| Aluno válido | Envia `ALUNO`; redireciona ao login com confirmação. |
| Professor com e-mail autorizado | Envia `PROFESSOR`; redireciona ao login com confirmação. |
| Professor sem autorização | Exibe o erro `403` retornado pela API. |
| E-mail já cadastrado | Exibe o erro `409` retornado pela API. |
| Campos rejeitados pela API | Exibe o resumo e os erros dos campos do `400`. |
| Falha de conexão | Exibe mensagem em português e permite tentar novamente. |
| Tela de 390 px e tema escuro | Campos legíveis e sem rolagem horizontal. |
| Voltar ao cadastro após sucesso | Senha vazia. |

Na implementação, lint e build passaram nos dois apps. A verificação de navegador usa respostas
simuladas para testar o contrato e os estados da interface; não substitui a confirmação de gravação
no MongoDB com a infraestrutura em execução.
