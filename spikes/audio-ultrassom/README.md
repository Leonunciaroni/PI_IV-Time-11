# Spike — áudio ultrassônico 18–20 kHz

Página estática (HTML + JS, sem dependências) para medir se notebooks emitem e celulares captam
tons de 18 a 20 kHz pelo navegador. Roteiro e critérios em
[docs/spikes/audio-ultrassom.md](../../docs/spikes/audio-ultrassom.md).

## Rodar no computador

```bash
python3 -m http.server 8765 --directory spikes/audio-ultrassom
# abra http://localhost:8765
```

`localhost` é contexto seguro, então o microfone funciona.

## Rodar no celular

O microfone exige HTTPS fora de `localhost`. Para testar no celular, hospede a pasta
em um serviço de páginas estáticas com HTTPS.

## Funções

| Bloco | Uso |
|---|---|
| Emissor | Tom senoidal em uma frequência fixa ou em varredura (18–20 kHz, passos de 250 Hz), com volume ajustável e rampa de 50 ms para não estalar. |
| Receptor | Microfone com cancelamento de eco, supressão de ruído e ganho automático **desligados**; FFT de 8192 pontos; SNR de cada frequência contra a mediana do ruído entre 15 e 22 kHz. |
| Autoteste | Tom digital de 19 kHz + ruído branco direto no analisador, sem microfone: valida a análise do navegador. |
| Registro | Guarda cada medição (dispositivo, distância, cenário, melhor frequência, SNR, audível) e exporta CSV. |

Uma frequência é marcada como detectada quando o SNR fica acima do limiar (padrão 15 dB) por 15
quadros seguidos (~0,25 s).
