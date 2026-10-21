# Fast Attendance — Aluno

Aplicação web Next.js. Decisões de stack em [docs/stack-apps.md](../../docs/stack-apps.md) e telas em
[docs/wireframes.md](../../docs/wireframes.md).

```bash
npm install
npm run dev        # http://localhost:3001/aluno
npm run lint
npm run build
```

Em desenvolvimento, abrir `http://localhost:3001/` redireciona para
`/aluno`. Os atalhos `/login` e `/cadastro` também levam às rotas com o prefixo
`/aluno`. Para testar o cadastro diretamente, acesse
`http://localhost:3001/cadastro`.
