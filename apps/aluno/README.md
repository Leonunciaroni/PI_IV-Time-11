# Fast Attendance — Aluno

Aplicação web Next.js. Decisões de stack em [docs/stack-apps.md](../../docs/stack-apps.md) e telas em
[docs/wireframes.md](../../docs/wireframes.md).

```bash
npm install
npm run dev        # http://localhost:3001/aluno
npm run lint
npm run build
```

A tela inicial autenticada lista turmas, frequência e chamadas abertas. Detalhes e limites da
integração com o check-in em [docs/inicio-aluno.md](../../docs/inicio-aluno.md).
Em desenvolvimento, `http://localhost:3001/` redireciona para `/aluno`.
