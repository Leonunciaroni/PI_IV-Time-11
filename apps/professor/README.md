<!--
Guilherme Preventi Correia
RA: 25001694
-->

# Fast Attendance — Professor

Aplicação web Next.js. Decisões de stack em [docs/stack-apps.md](../../docs/stack-apps.md) e telas em
[docs/wireframes.md](../../docs/wireframes.md).

```bash
npm install
npm run dev        # http://localhost:3000/professor
npm run lint
npm run build
```

Em desenvolvimento, abrir `http://localhost:3000/` redireciona para
`/professor`. Os atalhos `/login` e `/cadastro` também levam às rotas com o prefixo
`/professor`. Para testar o cadastro diretamente, acesse
`http://localhost:3000/cadastro`.
