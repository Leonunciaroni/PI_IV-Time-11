"use strict";

const elemento = (id) => document.getElementById(id);
const capturas = new Map();
let geracao = 0;

elemento("contexto").textContent = window.isSecureContext
  ? "Contexto seguro disponível. Toque nos botões e permita o acesso quando solicitado."
  : "Contexto inseguro. Abra esta página pelo endereço HTTPS do túnel para liberar as permissões.";

const mensagens = {
  NotAllowedError: "Permissão negada. Libere o acesso nas configurações deste site e do navegador no sistema.",
  NotFoundError: "Nenhum dispositivo disponível para este teste.",
  NotReadableError: "Dispositivo ocupado ou indisponível. Feche outros aplicativos que o utilizam.",
};

async function testarCaptura(tipo, restricoes) {
  const botao = elemento(tipo);
  const estado = elemento(`estado-${tipo}`);
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    estado.textContent = "Captura indisponível. Use HTTPS em um navegador com câmera e microfone.";
    return;
  }
  botao.disabled = true;
  estado.textContent = "Aguardando permissão…";
  const tentativa = geracao;
  try {
    const stream = await navigator.mediaDevices.getUserMedia(restricoes);
    // A permissão pode ser respondida depois de parar ou de ocultar a aba.
    if (tentativa !== geracao || document.hidden) {
      stream.getTracks().forEach((track) => track.stop());
      estado.textContent = "Teste cancelado. Dispositivo desligado.";
      botao.disabled = false;
      return;
    }
    capturas.set(tipo, stream);
    if (tipo === "camera") {
      elemento("video").srcObject = stream;
      elemento("video").hidden = false;
    }
    estado.textContent = tipo === "camera"
      ? "Câmera liberada. Confira a imagem abaixo."
      : "Microfone liberado e ativo. A captura não é gravada nem reproduzida.";
  } catch (erro) {
    estado.textContent = mensagens[erro.name] ?? `Não foi possível iniciar: ${erro.name}.`;
    botao.disabled = false;
  }
}

function parar() {
  geracao++;
  for (const [tipo, stream] of capturas) {
    stream.getTracks().forEach((track) => track.stop());
    elemento(`estado-${tipo}`).textContent = "Dispositivo desligado.";
    elemento(tipo).disabled = false;
  }
  capturas.clear();
  elemento("video").srcObject = null;
  elemento("video").hidden = true;
}

elemento("camera").addEventListener("click", () => testarCaptura("camera", {
  video: { facingMode: { ideal: "environment" } }, audio: false,
}));
elemento("microfone").addEventListener("click", () => testarCaptura("microfone", {
  audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false }, video: false,
}));
elemento("parar").addEventListener("click", parar);
window.addEventListener("pagehide", parar);
document.addEventListener("visibilitychange", () => { if (document.hidden) parar(); });

elemento("localizacao").addEventListener("click", () => {
  const estado = elemento("estado-localizacao");
  if (!window.isSecureContext || !navigator.geolocation) {
    estado.textContent = "Localização indisponível. Use HTTPS e habilite a localização no aparelho.";
    return;
  }
  elemento("localizacao").disabled = true;
  estado.textContent = "Aguardando localização…";
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      estado.textContent = `Localização liberada. Precisão aproximada: ${Math.round(coords.accuracy)} m.`;
      elemento("localizacao").disabled = false;
    },
    (erro) => {
      estado.textContent = erro.code === 1
        ? "Permissão negada. Libere a localização nas configurações deste site e do aparelho."
        : "Posição indisponível ou tempo esgotado. Confira o GPS e tente novamente.";
      elemento("localizacao").disabled = false;
    },
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
  );
});
