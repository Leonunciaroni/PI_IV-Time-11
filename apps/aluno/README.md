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
`/aluno`. O atalho `/login` leva a `/aluno/login`.
A área do aluno oferece somente login para contas existentes, sem tela de cadastro.
