<!-- Contribuição: Diogo Gonçalves Tonhosolo | RA: 25894007 | medições e registro dos resultados do spike de áudio
Arquivos relacionados com o mesmo crédito: ../../spikes/audio-ultrassom/medicoes/spike-audio-medicoes.csv, ../../spikes/audio-ultrassom/medicoes/spike-audio-medicoes-ambientes.csv -->
# Spike: áudio ultrassônico 18–20 kHz

| | |
|---|---|
| Frente | E — Áudio e Infraestrutura |
| Semana | 2 (04/10 a 11/10/2026) |
| Ferramenta | [`spikes/audio-ultrassom/`](../../spikes/audio-ultrassom/) |
| Situação | **Amostras registradas; validação ampla adiada como melhoria futura** |

## Objetivo

Antes de construir o modem FSK (Semana 3), responder se o canal sonoro é viável com o hardware
que já existe em sala: o notebook do professor (ou a caixa de som da sala) emitindo e o navegador
do celular do aluno captando.

## Perguntas

1. Os navegadores dos celulares da equipe captam 18–20 kHz com os filtros de voz desligados?
2. Qual o SNR no fundo de uma sala de 60 alunos (~10–12 m)?
3. Qual frequência da faixa tem melhor resposta para emissor e receptores?
4. O sinal vaza para a sala vizinha com a porta fechada?
5. Alguém da equipe ouve o tom no volume necessário?

## Hipóteses

- A maioria dos celulares capta até ~20 kHz (taxa de amostragem de 44,1 ou 48 kHz).
- Alto-falantes de notebook perdem resposta acima de 19 kHz; a caixa de som da sala deve ser melhor.
- Paredes e portas atenuam o suficiente para a sala vizinha ficar abaixo do limiar.

## Critérios de sucesso

| Critério | Meta |
|---|---|
| Detecção no fundo da sala (10–12 m, sala com conversa) | SNR ≥ 15 dB em pelo menos 4 dos modelos de celular da equipe |
| Sala vizinha, porta fechada | Não detectado (SNR < 15 dB) |
| Audibilidade | Ninguém da equipe ouve o tom no volume usado |
| Faixa escolhida | Uma sub-faixa de pelo menos 1 kHz dentro de 18–20 kHz que atenda aos três critérios acima |

Se os critérios falharem, o plano B já previsto no cronograma é o fallback para QR Code, mantendo
o modo sonoro como opcional.

## Procedimento

1. **Autoteste** em cada navegador receptor: o 19 kHz deve aparecer como detectado. Se falhar, o problema é do navegador, não do áudio.
2. Emissor no notebook, volume do sistema em 100% e volume da página em 30%. Varredura ligada.
3. Receptor a 1, 3, 6 e 10–12 m, em sala silenciosa e depois com conversa. Registrar a leitura em cada ponto.
4. Repetir o passo 3 com a caixa de som da sala como emissor.
5. Receptor na sala vizinha com a porta fechada e no corredor.
6. Perguntar a cada integrante se ouve o tom; reduzir o volume até ninguém ouvir e repetir o ponto de 10–12 m.
7. Exportar o CSV de cada receptor e anexar à issue da semana.

## Verificação já feita

Autoteste no Chrome (macOS, taxa de 96 kHz), com tom digital a -40 dBFS e ruído branco: 19 kHz
detectado com SNR de 23 dB, e as outras frequências da faixa abaixo de 9 dB. Isso valida a
análise, mas não o caminho acústico, que depende dos testes com alto-falante e microfone físicos.

## Resultados parciais

O primeiro arquivo, [spike-audio-medicoes.csv](../../spikes/audio-ultrassom/medicoes/spike-audio-medicoes.csv),
tem 18 leituras em **sala silenciosa**, a 3, 6, 9 e 12 m, com os rótulos Redmi 14 e
iPhone 12, 13, 14 e 15. O segundo,
[spike-audio-medicoes-ambientes.csv](../../spikes/audio-ultrassom/medicoes/spike-audio-medicoes-ambientes.csv),
tem 36 leituras em sala com conversa, sala vizinha e corredor. Nos dois arquivos,
`audivel` está como `Não` em todas as linhas, mas não consta quantos integrantes
avaliaram. Os CSVs não registram navegador, taxa de amostragem, tipo de emissor nem
volume do sistema ou da página.

Nas células abaixo, cada leitura está no formato frequência : SNR (dB) : detecção.

| Receptor (rótulo do CSV) | 3 m | 6 m | 9 m | 12 m |
|---|---|---|---|---|
| Redmi 14 | 18 kHz: 51,0 dB, sim; 19,75 kHz: 47,8 dB, sim; 20 kHz: 45,1 dB, não | 18,5 kHz: 20,9 dB, sim | 18 kHz: 31,7 dB, sim; 18,75/19,25/20 kHz: não | 19,5 kHz: 37,1 dB, sim |
| iPhone 12 | 18,75 kHz: 51,3 dB, não | — | — | — |
| iPhone 13 | 19 kHz: 47,8 dB, sim | — | — | — |
| iPhone 14 | 19,5 kHz: 54,3 dB, sim | 19,25 kHz: 30,5 dB, sim | 19,5 kHz: 36,3 dB, sim | 19,25 kHz: 34,0 dB, sim |
| iPhone 15 | — | 18,75 kHz: 41,4 dB, sim | 18,5 kHz: 38,1 dB, sim | 18,5 kHz: 35,5 dB, sim |

Resumo do segundo CSV:

| Cenário | Leituras | Detecções estáveis | SNR registrado |
|---|---:|---:|---:|
| Sala com conversa | 17, a 3, 6, 9 e 12 m | 13/17 | 33,2–53,9 dB |
| Sala vizinha, porta fechada | 14, a 3, 6, 9 e 12 m | 13/14 | 40,3–64,5 dB |
| Corredor | 5, a 6, 9 e 12 m | 4/5 | 24,5–63,3 dB |
