// Guilherme Preventi Correia
// RA: 25001694

import { configurar } from './modem.mjs';

/** Microfone → filtro de graves → AudioWorklet → Worker → token com CRC válido. */
export class ReceptorFsk {
  constructor({ configuracao = {}, aoToken = () => {}, aoErro = () => {} } = {}) {
    Object.assign(this, { configuracao, aoToken, aoErro });
    this.execucao = null;
  }

  // Chamado a partir de um clique. Retorna false se a captura foi cancelada enquanto iniciava.
  async iniciar() {
    if (this.execucao) throw new Error('O receptor já está iniciado.');
    if (!globalThis.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      throw new Error('O microfone exige HTTPS ou localhost.');
    }
    if (!globalThis.AudioContext || !globalThis.AudioWorkletNode || !globalThis.Worker) {
      throw new Error('Este navegador não oferece AudioWorklet e Worker para receber o sinal.');
    }
    const execucao = { contexto: new AudioContext({ latencyHint: 'interactive' }) };
    this.execucao = execucao;
    try {
      const config = configurar(this.configuracao, execucao.contexto.sampleRate);
      const retomada = execucao.contexto.resume();
      const captura = navigator.mediaDevices.getUserMedia({ audio: {
        channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false,
      } }).then((stream) => {
        if (this.execucao !== execucao) stream.getTracks().forEach((track) => track.stop());
        else execucao.stream = stream;
      });
      await Promise.all([retomada, captura]);
      if (this.execucao !== execucao) return false;
      await execucao.contexto.audioWorklet.addModule(new URL('./captura-worklet.mjs', import.meta.url));
      if (this.execucao !== execucao) return false;
      const worker = execucao.worker = new Worker(new URL('./decodificador-worker.mjs', import.meta.url), { type: 'module' });
      const pronto = await new Promise((resolve, reject) => {
        let iniciado = false;
        const limite = setTimeout(() => concluir(new Error('O decodificador não iniciou em 5 segundos.')), 5000);
        const concluir = (erro, valor) => {
          clearTimeout(limite);
          execucao.cancelarInicio = null;
          if (erro) reject(erro); else resolve(valor);
        };
        execucao.cancelarInicio = () => concluir(null, false);
        const falhar = (erro) => {
          if (this.execucao !== execucao) return;
          if (!iniciado) concluir(erro);
          else { this.parar(); this.aoErro(erro); }
        };
        worker.onmessage = ({ data }) => {
          if (this.execucao !== execucao) return;
          if (data.tipo === 'pronto') { iniciado = true; concluir(null, true); }
          else if (data.tipo === 'token') this.aoToken(data.token);
          else if (data.tipo === 'erro') falhar(new Error(data.mensagem));
        };
        worker.onerror = (evento) => { evento.preventDefault(); falhar(new Error('Falha no Worker de áudio.')); };
        worker.postMessage({ tipo: 'iniciar', taxaAmostragem: execucao.contexto.sampleRate, configuracao: config });
      });
      if (!pronto || this.execucao !== execucao) return false;
      const fonte = execucao.fonte = execucao.contexto.createMediaStreamSource(execucao.stream);
      const filtro = execucao.filtro = execucao.contexto.createBiquadFilter();
      filtro.type = 'highpass';
      filtro.frequency.value = Math.min(config.frequencia0, config.frequencia1) - 1000;
      filtro.Q.value = Math.SQRT1_2;
      const noCaptura = execucao.node = new AudioWorkletNode(execucao.contexto, 'captura-fsk');
      noCaptura.onprocessorerror = () => {
        if (this.execucao === execucao) {
          this.parar();
          this.aoErro(new Error('O processamento da captura de áudio falhou. Inicie a escuta novamente.'));
        }
      };
      noCaptura.port.onmessage = ({ data: amostras }) => {
        if (this.execucao === execucao) worker.postMessage({ tipo: 'amostras', amostras }, [amostras.buffer]);
      };
      fonte.connect(filtro).connect(noCaptura).connect(execucao.contexto.destination);
      for (const track of execucao.stream.getTracks()) track.addEventListener('ended', () => {
        if (this.execucao === execucao) {
          this.parar();
          this.aoErro(new Error('O microfone foi desligado pelo navegador ou pelo aparelho.'));
        }
      }, { once: true });
      return true;
    } catch (erro) {
      if (this.execucao !== execucao) return false;
      await this.parar();
      throw erro;
    }
  }

  parar() {
    const execucao = this.execucao;
    this.execucao = null;
    if (!execucao) return Promise.resolve();
    execucao.cancelarInicio?.();
    execucao.worker?.terminate();
    execucao.node?.port.close();
    execucao.node?.disconnect();
    execucao.filtro?.disconnect();
    execucao.fonte?.disconnect();
    execucao.stream?.getTracks().forEach((track) => track.stop());
    return execucao.contexto.close().catch(() => {});
  }
}
