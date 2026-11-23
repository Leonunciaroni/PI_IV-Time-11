// Autoria: Diogo Gonçalves Tonhosolo | RA: 25894007
import { DecodificadorFsk } from './modem.mjs';

let decodificador;
globalThis.onmessage = ({ data }) => {
  try {
    if (data.tipo === 'iniciar') {
      decodificador = new DecodificadorFsk(data.taxaAmostragem, data.configuracao);
      globalThis.postMessage({ tipo: 'pronto' });
    } else if (data.tipo === 'amostras' && decodificador) {
      for (const token of decodificador.processar(data.amostras)) {
        globalThis.postMessage({ tipo: 'token', token });
      }
    } else throw new Error('Decodificador não inicializado.');
  } catch (erro) {
    globalThis.postMessage({ tipo: 'erro', mensagem: erro.message });
  }
};
