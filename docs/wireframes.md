# Wireframes e descrição do funcionamento

Wireframes de baixa fidelidade das telas previstas na seção 3.2.6 do Relatório de Ideação.
A aplicação do **professor** é pensada para notebook ou projetor (a tela de QR Code e a de som
ficam visíveis para a turma); a do **aluno**, para celular.

Princípios que guiam as telas, derivados da síntese de insights:

- **Zero configuração por aula:** abrir uma chamada exige no máximo três toques (turma → modo → abrir).
- **Contingência sempre à mão:** em qualquer modo, o professor pode trocar para a chamada clássica sem perder as presenças já registradas.
- **Feedback imediato:** toda ação do aluno termina em uma tela de sucesso ou de erro com o motivo (fraqueza apontada na SWOT).

## Mapa de navegação

```
PROFESSOR                                    ALUNO
─────────                                    ─────
[P1 Login] ──► [P2 Turmas] ──► [P3 Turma]    [A1 Login] ──► [A2 Início]
                   │              │  │                         │     │
                   ▼              │  ▼                         ▼     ▼
             [P2b Nova turma]     │ [P4 Matrícula]       [A3 Check-in] [A4 Histórico]
                                  ▼                       ├─ QR Code
                          [P5 Abrir aula]                 └─ Som
                            │     │     │                        │
                            ▼     ▼     ▼                        ▼
                      [P6 Clássica] [P7 QR] [P8 Som]      [A3b Resultado]
                                  │
                                  ▼
                        [P9 Dashboard da turma]
```

---

## Aplicação do professor

### P1 — Login

```
┌──────────────────────────────────────────────┐
│                                              │
│              FAST ATTENDANCE                 │
│              Área do professor               │
│                                              │
│   E-mail   [______________________________]  │
│   Senha    [______________________________]  │
│                                              │
│            [        Entrar         ]         │
│                                              │
│   ⚠ E-mail ou senha incorretos.              │  ← só aparece em caso de erro
│                                              │
│   É aluno? Acesse /aluno                     │
└──────────────────────────────────────────────┘
```

O professor informa e-mail e senha; a aplicação chama `POST /api/auth/login`. Em caso de
sucesso guarda o JWT e vai para **P2**. Se a conta for de aluno, a tela informa que o acesso
correto é pela aplicação do aluno. O botão fica desabilitado durante a requisição.

### P2 — Minhas turmas

```
┌──────────────────────────────────────────────────────────────────┐
│ FAST ATTENDANCE                             Prof. Matheus  [Sair]│
├──────────────────────────────────────────────────────────────────┤
│ Minhas turmas                                     [+ Nova turma] │
│                                                                  │
│ ┌────────────────────────────┐  ┌────────────────────────────┐   │
│ │ Engenharia de Software I   │  │ Estrutura de Dados         │   │
│ │ Turma A · Seg 19:00        │  │ Turma B · Qua 21:00        │   │
│ │ 58 alunos · freq. 87%      │  │ 42 alunos · freq. 91%      │   │
│ │ [Abrir chamada] [Detalhes] │  │ [Abrir chamada] [Detalhes] │   │
│ └────────────────────────────┘  └────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

Lista as turmas do professor com frequência média. **Abrir chamada** leva direto à **P5** com a
turma já escolhida. **+ Nova turma** abre o formulário P2b (nome, disciplina, horário padrão).

### P3 — Detalhes da turma

```
┌──────────────────────────────────────────────────────────────┐
│ ← Turmas   Engenharia de Software I · Turma A                │
├──────────────────────────────────────────────────────────────┤
│ [Abrir chamada]  [Matricular alunos]  [Exportar CSV]         │
│                                                              │
│ Aluno                    Matrícula    Frequência             │
│ ───────────────────────────────────────────────────          │
│ Ana Souza                24001234     95%                    │
│ Bruno Lima               24001777     71%  ⚠ abaixo de 75%   │
│ ...                                                          │
└──────────────────────────────────────────────────────────────┘
```

### P4 — Matrícula de alunos

```
┌──────────────────────────────────────────────────────────────┐
│ ← Turma A   Matricular alunos                                │
├──────────────────────────────────────────────────────────────┤
│ Buscar aluno por nome ou matrícula [___________________] 🔍  │
│                                                              │
│ ☐ Carla Dias       24002001                                  │
│ ☐ Diego Alves      24002002                                  │
│                                                              │
│ ── ou ──   [Importar lista CSV]                              │
│                                    [Matricular selecionados] │
└──────────────────────────────────────────────────────────────┘
```

### P5 — Abrir aula (seleção do modo)

```
┌──────────────────────────────────────────────────────────────┐
│ ← Turma A   Abrir chamada                                    │
├──────────────────────────────────────────────────────────────┤
│ Como será a chamada de hoje?                                 │
│                                                              │
│ ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│ │  [=] Lista   │  │  [▣] QR      │  │  ))) Som     │         │
│ │  Clássica    │  │  QR Code     │  │  Ultrassom   │         │
│ │ marcar lista │  │ + localização│  │ sem foto     │         │
│ └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                              │
│ Sala (QR Code):  [ H15 - Sala 203  ▾ ]                       │
│                                                              │
│                     [   Abrir chamada   ]                    │
└──────────────────────────────────────────────────────────────┘
```

O professor escolhe um dos três modos; o seletor de sala só aparece no modo QR Code. Ao abrir,
a aplicação envia `sessao.abrir` pelo WebSocket e recebe `sessao.aberta` com a configuração TOTP.

### P6 — Chamada clássica

```
┌──────────────────────────────────────────────────────────────┐
│ Turma A · Chamada clássica        31/58 presentes  [Encerrar] │
├──────────────────────────────────────────────────────────────┤
│ [✓ Marcar todos presentes]   [Limpar]    Buscar [________]   │
│                                                              │
│ ☑ Ana Souza                                                  │
│ ☐ Bruno Lima                                                 │
│ ☑ Carla Dias                                                 │
│ ...                                                          │
└──────────────────────────────────────────────────────────────┘
```

Marcação em lote: **Marcar todos presentes** e depois desmarcar as exceções. Cada toque envia
`presenca.marcar`; o contador no topo vem dos eventos `presenca.atualizada`.

### P7 — QR Code rotativo com painel ao vivo

```
┌──────────────────────────────────────────────────────────────┐
│ Turma A · QR Code                 31/58 presentes  [Encerrar] │
├───────────────────────────────────┬──────────────────────────┤
│                                   │ Presentes (ao vivo)      │
│        ██▀▀▀▀██  ▄▄  ██▀▀▀▀██     │ 19:02:11 Ana Souza       │
│        ██ ██ ██ ▀▄▀▄ ██ ██ ██     │ 19:02:09 Carla Dias      │
│        ██▄▄▄▄██ ▄▀▄  ██▄▄▄▄██     │ 19:02:08 Diego Alves     │
│        ▄▄ ▀▄ ▄▄▀▄▀▄▀ ▄▀▄ ▄▄▀▄     │ ...                      │
│        ██▀▀▀▀██ ▀▄▀▄▀▄ ▀▄▀▄▀▄     │                          │
│                                   │                          │
│   Novo código em  ▓▓▓▓▓▓░░░  9 s  │ [Trocar para clássica]   │
└───────────────────────────────────┴──────────────────────────┘
```

O QR Code (`FA1:<sessaoId>:<token>`) é gerado localmente a cada janela TOTP de 15 s, com barra de
progresso. A lista à direita é alimentada pelos eventos `presenca.atualizada`. **Trocar para
clássica** é a contingência caso o QR não funcione.

### P8 — Emissão do sinal sonoro

```
┌──────────────────────────────────────────────────────────────┐
│ Turma A · Onda sonora             31/58 presentes  [Encerrar] │
├───────────────────────────────────┬──────────────────────────┤
│                                   │ Presentes (ao vivo)      │
│            )))  ◉  (((            │ 19:02:11 Ana Souza       │
│                                   │ ...                      │
│      Transmitindo 18–20 kHz       │                          │
│      Volume  [━━━━━━━━░░]         │                          │
│                                   │                          │
│   [ ■ Pausar ]                    │ [Trocar para QR Code]    │
│                                   │ [Trocar para clássica]   │
└───────────────────────────────────┴──────────────────────────┘
```

O navegador do professor emite o token em loop com *fade in/out*. Exige um toque inicial para
liberar o áudio do navegador. Se os alunos do fundo não captarem o sinal, o professor troca para
QR Code (fallback previsto no cronograma).

### P9 — Dashboard de frequência da turma

```
┌──────────────────────────────────────────────────────────────┐
│ ← Turma A   Frequência                        [Exportar CSV] │
├──────────────────────────────────────────────────────────────┤
│ Aulas dadas: 12   Frequência média: 87%   Abaixo de 75%: 4   │
│                                                              │
│ Frequência por aula                                          │
│ 100%┤ ▇ ▇ ▆ ▇ ▇ ▅ ▇ ▆ ▇ ▇ ▆ ▇                                 │
│   0%┼─────────────────────────                               │
│                                                              │
│ Uso por modo:  Clássica 2 · QR Code 7 · Som 3                │
│                                                              │
│ Alunos abaixo do mínimo                                      │
│ Bruno Lima   71%   ·   Eva Rocha   68%   ...                 │
└──────────────────────────────────────────────────────────────┘
```

Atende a coordenação: percentual por aluno, destaque de quem está abaixo dos 75% e exportação em
CSV. O registro do modo usado em cada aula permite comparar a eficácia dos três modos.

Fica em `/turmas/{id}/frequencia`, aberta pelo botão **Frequência** da P3, e usa
`GET /api/turmas/{id}/estatisticas`. As barras seguem a ordem das aulas; a aula abaixo de 75% fica
em vermelho e cada barra mostra data, modo e presentes ao passar o mouse (e para o leitor de tela).
**Exportar CSV** baixa o consolidado (`GET /api/turmas/{id}/exportacao/consolidado`). Sem aulas
encerradas, a tela mostra só os números, com "—" na frequência média, e um aviso no lugar do
gráfico.

---

## Aplicação do aluno (celular)

### A1 — Login

```
┌────────────────────────┐
│                        │
│    FAST ATTENDANCE     │
│    Área do aluno       │
│                        │
│ E-mail                 │
│ [____________________] │
│ Senha                  │
│ [____________________] │
│                        │
│ [       Entrar       ] │
│                        │
│ ⚠ E-mail ou senha      │  ← só em caso de erro
│   incorretos.          │
│                        │
│ É professor?           │
│ Acesse /professor      │
└────────────────────────┘
```

Igual à P1, para o papel `ALUNO`. Na primeira execução, a aplicação gera e guarda o
`dispositivoId` usado no check-in.

### A2 — Início

```
┌────────────────────────┐
│ Olá, Ana        [Sair] │
├────────────────────────┤
│ ● Chamada aberta       │
│ Eng. Software I · A    │
│ Modo: QR Code          │
│ [  Confirmar presença ]│
├────────────────────────┤
│ Minhas turmas          │
│ Eng. Software I   95%  │
│ Estrut. de Dados  88%  │
│                        │
│ [ Ver histórico ]      │
└────────────────────────┘
```

Destaca a chamada aberta de alguma turma do aluno e o modo escolhido pelo professor; o botão leva
à A3 já no canal correto.

### A3 — Check-in (QR Code ou som)

```
┌────────────────────────┐   ┌────────────────────────┐
│ ← Confirmar presença   │   │ ← Confirmar presença   │
├────────────────────────┤   ├────────────────────────┤
│ ┌────────────────────┐ │   │                        │
│ │                    │ │   │      ((( 🎤 )))        │
│ │   [ câmera ]       │ │   │                        │
│ │   aponte para o QR │ │   │   Ouvindo o sinal...   │
│ │                    │ │   │   ▁▃▅▇▅▃▁              │
│ └────────────────────┘ │   │                        │
│ 📍 Localização: ok     │   │ Mantenha o celular     │
│                        │   │ desbloqueado na sala.  │
└────────────────────────┘   └────────────────────────┘
        modo QR Code                  modo Som
```

- **QR Code:** pede câmera e localização; ao ler o código, envia `checkin` com token e coordenadas.
- **Som:** pede o microfone (com filtros de voz desligados), decodifica o token e envia `checkin`
  automaticamente, sem ação do aluno.

### A3b — Resultado

```
┌────────────────────────┐   ┌────────────────────────┐
│                        │   │                        │
│          ✅            │   │          ❌            │
│  Presença confirmada   │   │  Não foi possível      │
│  Eng. Software I · A   │   │  confirmar             │
│  19:02:11              │   │                        │
│                        │   │  Código expirado.      │
│                        │   │  Leia o QR novamente.  │
│ [   Voltar ao início  ]│   │ [  Tentar novamente   ]│
└────────────────────────┘   └────────────────────────┘
```

A mensagem de erro traduz o código do protocolo (`TOKEN_EXPIRADO`, `FORA_DO_RAIO`,
`PRESENCA_DUPLICADA`, `DISPOSITIVO_JA_UTILIZADO`…) em uma instrução clara para o aluno.

### A4 — Histórico de frequência

```
┌────────────────────────┐
│ ← Histórico            │
├────────────────────────┤
│ Eng. Software I · 95%  │
│ 06/10  ✅ QR Code      │
│ 29/09  ✅ Som          │
│ 22/09  ❌ Ausente      │
│ 15/09  ✅ Clássica     │
│ ...                    │
└────────────────────────┘
```

Todas as aulas da turma com o status do aluno e o modo usado em cada uma.

---

## Fluxo principal: chamada por QR Code

1. Professor: **P2** → *Abrir chamada* → **P5** escolhe *QR Code* e a sala → **P7**.
2. Aluno: **A2** mostra *Chamada aberta* → **A3** lê o QR e envia a localização.
3. Servidor valida token (janela atual ou anterior), matrícula, raio da sala, dispositivo e duplicidade.
4. Aluno vê **A3b** de sucesso; professor vê o nome surgir na lista ao vivo da **P7**.
5. Professor toca *Encerrar*; nenhuma presença nova é aceita.

Meta do objetivo 1: os passos 2 a 4 acontecem em paralelo para toda a turma, e a chamada inteira
dura menos de um minuto.
