/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Spike da Semana 2: verifica se notebooks emitem e celulares captam tons de 18 a 20 kHz pelo
// navegador, com que relação sinal-ruído (SNR) e a que distância. Não codifica dados: o modem
// FSK é o entregável da Semana 3.

const FREQUENCIAS = [18000, 18250, 18500, 18750, 19000, 19250, 19500, 19750, 20000];
const FAIXA_ESPECTRO = [15000, 22000];
// Rampa de volume para evitar o "clique" audível ao ligar/desligar o oscilador
const RAMPA_S = 0.05;
const FFT = 8192;
// Piso para níveis de silêncio digital (-Infinity dB), mantendo o SNR finito
const PISO_DB = -160;
// Quadros consecutivos acima do limiar para considerar detectado (~0,25 s a 60 fps)
const QUADROS_PARA_DETECTAR = 15;

const $ = (id) => document.getElementById(id);
const registros = [];

// ---------- Ambiente ----------

function verificarAmbiente() {
  const avisos = [];
  if (!window.isSecureContext) {
    avisos.push("Página fora de contexto seguro: o microfone só funciona em https:// ou localhost.");
  }
  if (!window.AudioContext) avisos.push("Navegador sem Web Audio API.");
  const ctx = new AudioContext();
  const taxa = ctx.sampleRate;
  ctx.close();
  if (taxa / 2 < 20500) avisos.push(`Taxa de amostragem ${taxa} Hz: Nyquist abaixo de 20,5 kHz.`);
  $("ambiente").innerHTML = `Taxa de amostragem: <b>${taxa} Hz</b> (Nyquist ${(taxa / 2 / 1000).toFixed(2)} kHz). `
    + avisos.map((a) => `<span class="aviso">${a}</span>`).join(" ");
}

// ---------- Emissor ----------

let emissor = null;

function iniciarEmissor() {
  const ctx = new AudioContext();
  const osc = ctx.createOscillator();
  const ganho = ctx.createGain();
  osc.type = "sine";
  osc.frequency.value = Number($("frequencia").value);
  ganho.gain.value = 0;
  osc.connect(ganho).connect(ctx.destination);
  osc.start();
  ganho.gain.linearRampToValueAtTime(Number($("volume").value), ctx.currentTime + RAMPA_S);

  let indice = FREQUENCIAS.indexOf(osc.frequency.value);
  const varredura = $("varredura").checked
    ? setInterval(() => {
        // Fade out → troca → fade in, para a troca também não estalar
        const t = ctx.currentTime;
        const volume = Number($("volume").value);
        ganho.gain.setValueAtTime(volume, t);
        ganho.gain.linearRampToValueAtTime(0, t + RAMPA_S);
        indice = (indice + 1) % FREQUENCIAS.length;
        osc.frequency.setValueAtTime(FREQUENCIAS[indice], t + RAMPA_S);
        ganho.gain.linearRampToValueAtTime(volume, t + 2 * RAMPA_S);
        $("estado-emissor").textContent = `Emitindo ${FREQUENCIAS[indice] / 1000} kHz (varredura)`;
      }, 1000)
    : null;

  emissor = { ctx, osc, ganho, varredura };
  $("estado-emissor").textContent = `Emitindo ${osc.frequency.value / 1000} kHz`;
  $("emitir").disabled = true;
  $("parar").disabled = false;
}

function pararEmissor() {
  if (!emissor) return;
  const { ctx, osc, ganho, varredura } = emissor;
  clearInterval(varredura);
  ganho.gain.cancelScheduledValues(ctx.currentTime);
  ganho.gain.setValueAtTime(ganho.gain.value, ctx.currentTime);
  ganho.gain.linearRampToValueAtTime(0, ctx.currentTime + RAMPA_S);
  osc.stop(ctx.currentTime + RAMPA_S + 0.01);
  osc.onended = () => ctx.close();
  emissor = null;
  $("estado-emissor").textContent = "Parado.";
  $("emitir").disabled = false;
  $("parar").disabled = true;
}

// ---------- Receptor ----------

let receptor = null;

async function iniciarReceptor() {
  // Criado ainda dentro do gesto do usuário: depois do await o navegador o deixaria suspenso
  const ctx = new AudioContext();
  let stream;
  try {
    // Filtros de voz desligados: eles atenuam ou removem as altas frequências
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
    });
  } catch (e) {
    ctx.close();
    $("leituras").innerHTML = `<tr><td colspan="4" class="aviso">Microfone indisponível: ${e.message}</td></tr>`;
    return;
  }
  await ctx.resume();
  conectarAnalisador(ctx, ctx.createMediaStreamSource(stream), () => stream.getTracks().forEach((t) => t.stop()));
}

/**
 * Autoteste sem microfone: tom de 19 kHz a -40 dBFS somado a ruído branco entra direto no
 * analisador. Valida a análise do navegador antes das medições com alto-falante e microfone.
 */
async function iniciarAutoteste() {
  const ctx = new AudioContext();
  await ctx.resume();
  const misturador = ctx.createGain();

  const tom = ctx.createOscillator();
  tom.frequency.value = 19000;
  const ganhoTom = ctx.createGain();
  ganhoTom.gain.value = 0.01;
  tom.connect(ganhoTom).connect(misturador);

  const amostras = ctx.sampleRate * 2;
  const buffer = ctx.createBuffer(1, amostras, ctx.sampleRate);
  const canal = buffer.getChannelData(0);
  for (let i = 0; i < amostras; i++) canal[i] = (Math.random() * 2 - 1) * 0.05;
  const ruido = ctx.createBufferSource();
  ruido.buffer = buffer;
  ruido.loop = true;
  ruido.connect(misturador);

  tom.start();
  ruido.start();
  conectarAnalisador(ctx, misturador, () => { tom.stop(); ruido.stop(); });
}

function conectarAnalisador(ctx, fonte, aoParar) {
  const analisador = ctx.createAnalyser();
  analisador.fftSize = FFT;
  analisador.smoothingTimeConstant = 0;
  fonte.connect(analisador);

  receptor = {
    ctx, analisador, aoParar,
    espectro: new Float32Array(analisador.frequencyBinCount),
    consecutivos: new Map(FREQUENCIAS.map((f) => [f, 0])),
    ultimaLeitura: [],
    quadro: 0,
  };
  $("ouvir").disabled = true;
  $("autoteste").disabled = true;
  $("parar-ouvir").disabled = false;
  $("registrar").disabled = false;
  analisar();
}

function pararReceptor() {
  if (!receptor) return;
  cancelAnimationFrame(receptor.quadro);
  receptor.aoParar();
  receptor.ctx.close();
  receptor = null;
  $("ouvir").disabled = false;
  $("autoteste").disabled = false;
  $("parar-ouvir").disabled = true;
  $("registrar").disabled = true;
}

const binDe = (freq, ctx) => Math.round((freq / (ctx.sampleRate / 2)) * (FFT / 2));
const freqDe = (bin, ctx) => (bin * ctx.sampleRate) / FFT;

function mediana(valores) {
  const v = [...valores].sort((a, b) => a - b);
  return v[Math.floor(v.length / 2)];
}

/** Lê o espectro e calcula, para cada frequência-alvo, o nível e o SNR contra o ruído da faixa. */
function lerFrequencias() {
  const { ctx, analisador, espectro, consecutivos } = receptor;
  analisador.getFloatFrequencyData(espectro);
  for (let i = 0; i < espectro.length; i++) {
    if (!(espectro[i] > PISO_DB)) espectro[i] = PISO_DB;
  }

  const [ini, fim] = FAIXA_ESPECTRO.map((f) => binDe(f, ctx));
  const alvos = new Set(FREQUENCIAS.flatMap((f) => {
    const b = binDe(f, ctx);
    return [b - 2, b - 1, b, b + 1, b + 2];
  }));
  const ruido = mediana(espectro.slice(ini, fim).filter((_, i) => !alvos.has(ini + i)));
  const limiar = Number($("limiar").value);

  return FREQUENCIAS.map((f) => {
    const b = binDe(f, ctx);
    // Pico em ±1 bin: a frequência emitida pode cair entre dois bins da FFT
    const nivel = Math.max(espectro[b - 1], espectro[b], espectro[b + 1]);
    const snr = nivel - ruido;
    const seguidos = snr >= limiar ? consecutivos.get(f) + 1 : 0;
    consecutivos.set(f, seguidos);
    return { f, nivel, snr, detectado: seguidos >= QUADROS_PARA_DETECTAR };
  });
}

function desenharEspectro() {
  const { ctx, espectro } = receptor;
  const canvas = $("espectro");
  const g = canvas.getContext("2d");
  const [ini, fim] = FAIXA_ESPECTRO.map((f) => binDe(f, ctx));
  const [minDb, maxDb] = [-140, -20];
  g.clearRect(0, 0, canvas.width, canvas.height);

  g.fillStyle = "#3b82f633";
  const [a, b] = [18000, 20000].map((f) => ((binDe(f, ctx) - ini) / (fim - ini)) * canvas.width);
  g.fillRect(a, 0, b - a, canvas.height);

  g.strokeStyle = "#2563eb";
  g.beginPath();
  for (let i = ini; i < fim; i++) {
    const x = ((i - ini) / (fim - ini)) * canvas.width;
    const y = canvas.height * (1 - (espectro[i] - minDb) / (maxDb - minDb));
    i === ini ? g.moveTo(x, y) : g.lineTo(x, y);
  }
  g.stroke();

  g.fillStyle = getComputedStyle(document.body).color;
  g.font = "11px system-ui";
  for (let f = 15000; f <= 22000; f += 1000) {
    g.fillText(`${f / 1000}k`, ((binDe(f, ctx) - ini) / (fim - ini)) * canvas.width + 2, canvas.height - 4);
  }
  g.fillText(`${freqDe(ini, ctx).toFixed(0)}–${freqDe(fim, ctx).toFixed(0)} Hz`, 4, 12);
}

function analisar() {
  if (!receptor) return;
  const leitura = lerFrequencias();
  receptor.ultimaLeitura = leitura;
  desenharEspectro();
  $("leituras").innerHTML = leitura.map(({ f, nivel, snr, detectado }) =>
    `<tr><td>${(f / 1000).toFixed(2)} kHz</td><td>${nivel.toFixed(1)}</td><td>${snr.toFixed(1)}</td>`
    + `<td class="${detectado ? "sim" : "nao"}">${detectado ? "sim" : "não"}</td></tr>`).join("");
  receptor.quadro = requestAnimationFrame(analisar);
}

// ---------- Registro ----------

function registrar() {
  const leitura = receptor?.ultimaLeitura ?? [];
  if (!leitura.length) return;
  const melhor = leitura.reduce((a, b) => (b.snr > a.snr ? b : a));
  registros.push({
    hora: new Date().toLocaleTimeString("pt-BR"),
    dispositivo: $("dispositivo").value.trim() || "(não informado)",
    distancia: $("distancia").value || "",
    cenario: $("cenario").value,
    frequencia: melhor.f,
    snr: melhor.snr.toFixed(1),
    detectou: melhor.detectado ? "sim" : "não",
    audivel: $("audivel").value,
  });
  $("registros").innerHTML = registros.map((r) =>
    `<tr><td>${r.hora}</td><td>${escapar(r.dispositivo)}</td><td>${r.distancia}</td><td>${r.cenario}</td>`
    + `<td>${r.frequencia / 1000} kHz</td><td>${r.snr}</td><td>${r.detectou}</td><td>${r.audivel}</td></tr>`).join("");
  $("exportar").disabled = false;
}

function escapar(texto) {
  return texto.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}

function exportar() {
  const campos = ["hora", "dispositivo", "distancia", "cenario", "frequencia", "snr", "detectou", "audivel"];
  const linhas = [campos.join(";"), ...registros.map((r) => campos.map((c) => `"${String(r[c]).replace(/"/g, '""')}"`).join(";"))];
  const url = URL.createObjectURL(new Blob([linhas.join("\n")], { type: "text/csv;charset=utf-8" }));
  const a = Object.assign(document.createElement("a"), { href: url, download: "spike-audio-medicoes.csv" });
  a.click();
  URL.revokeObjectURL(url);
}

// ---------- Inicialização ----------

$("frequencia").innerHTML = FREQUENCIAS.map((f) =>
  `<option value="${f}" ${f === 19000 ? "selected" : ""}>${(f / 1000).toFixed(2)} kHz</option>`).join("");
$("volume").addEventListener("input", (e) => {
  $("volume-valor").textContent = `${Math.round(e.target.value * 100)}%`;
  if (emissor) emissor.ganho.gain.setTargetAtTime(Number(e.target.value), emissor.ctx.currentTime, 0.02);
});
$("frequencia").addEventListener("change", (e) => {
  if (emissor && !$("varredura").checked) {
    emissor.osc.frequency.setValueAtTime(Number(e.target.value), emissor.ctx.currentTime);
    $("estado-emissor").textContent = `Emitindo ${e.target.value / 1000} kHz`;
  }
});
$("emitir").addEventListener("click", iniciarEmissor);
$("parar").addEventListener("click", pararEmissor);
$("ouvir").addEventListener("click", iniciarReceptor);
$("autoteste").addEventListener("click", iniciarAutoteste);
$("parar-ouvir").addEventListener("click", pararReceptor);
$("registrar").addEventListener("click", registrar);
$("exportar").addEventListener("click", exportar);
verificarAmbiente();
