// Autoria: Diogo Gonçalves Tonhosolo | RA: 25894007
import { criarModem, configurar, codificarAmostras, DecodificadorFsk } from '../modem.mjs';
import { EmissorToken, RAMPA_SEGUNDOS } from '../emissor.mjs';
import { ReceptorFsk } from '../receptor.mjs';

const $ = (id) => document.getElementById(id);
let emissao = null, receptor = null, testando = false;
const erro = (e) => { $('erro').textContent = e.message ?? String(e); };

$('form-configuracao').addEventListener('submit', (evento) => evento.preventDefault());
$('form-configuracao').addEventListener('reset', () => {
  $('erro').textContent = '';
  $('estado-autoteste').classList.remove('falha');
  $('estado-autoteste').textContent = 'Configuração inicial restaurada. Execute o autoteste novamente.';
  // O evento reset ocorre antes de o navegador restaurar os valores dos campos.
  queueMicrotask(() => { $('volume-valor').textContent = `${Math.round(Number($('volume').value) * 100)}%`; });
});

$('ambiente').textContent = window.isSecureContext
  ? 'Contexto seguro disponível para o microfone.'
  : 'Para usar o microfone no celular, abra por HTTPS. No computador, use localhost.';

function lerConfiguracao() {
  const configuracao = { frequencia0: Number($('frequencia0').value), frequencia1: Number($('frequencia1').value),
    duracaoSimboloMs: Number($('simbolo').value) };
  configurar(configuracao);
  return configuracao;
}

function lerToken() {
  const token = $('token').value;
  if (!/^[0-9]{6}$/.test(token)) throw new Error('Digite exatamente seis dígitos no token de teste.');
  return token;
}

function atualizarBotoes() {
  $('configuracao').disabled = Boolean(emissao || receptor || testando);
  $('emitir').disabled = Boolean(emissao || testando);
  $('parar-emissao').disabled = !emissao;
  $('ouvir').disabled = Boolean(receptor || testando);
  $('parar-recepcao').disabled = !receptor;
  $('autoteste').disabled = Boolean(emissao || receptor || testando);
}

async function pararEmissao() {
  const atual = emissao;
  emissao = null;
  atualizarBotoes();
  $('estado-emissao').textContent = 'Emissão parada.';
  if (atual) {
    atual.controlador.parar();
    await new Promise((resolve) => setTimeout(resolve, RAMPA_SEGUNDOS * 1000 + 20));
    await atual.contexto.close().catch(() => {});
  }
}

async function pararRecepcao() {
  const atual = receptor;
  receptor = null;
  atualizarBotoes();
  $('estado-recepcao').textContent = 'Microfone desligado.';
  await atual?.parar();
}

$('emitir').addEventListener('click', async () => {
  $('erro').textContent = '';
  let atual;
  try {
    const token = lerToken(), configuracao = lerConfiguracao();
    if (!window.AudioContext) throw new Error('Este navegador não oferece Web Audio.');
    const contexto = new AudioContext();
    atual = { contexto };
    atual.controlador = new EmissorToken({ contexto, periodoSegundos: 15,
      gerarToken: async () => token, codificar: criarModem(configuracao).codificar,
      volume: Number($('volume').value), aoErro: (e) => {
        if (emissao === atual) { erro(e); void pararEmissao(); }
      } });
    emissao = atual;
    atualizarBotoes();
    $('estado-emissao').textContent = 'Iniciando…';
    await atual.controlador.iniciar();
    if (emissao === atual) $('estado-emissao').textContent = `Emissão ativa: token ${token}, janelas de 15 s. Aguarde a próxima fronteira.`;
  } catch (e) {
    if (!atual || emissao === atual) { erro(e); await pararEmissao(); }
  }
});

$('ouvir').addEventListener('click', async () => {
  $('erro').textContent = '';
  let atual;
  try {
    const configuracao = lerConfiguracao();
    atual = new ReceptorFsk({ configuracao, aoToken: (token) => {
      $('token-recebido').textContent = token;
      const item = document.createElement('li');
      item.textContent = `${new Date().toLocaleTimeString('pt-BR')} · ${token} · CRC válido`;
      $('recebidos').prepend(item);
      while ($('recebidos').children.length > 5) $('recebidos').lastElementChild.remove();
    }, aoErro: (e) => {
      if (receptor === atual) { erro(e); void pararRecepcao(); }
    } });
    receptor = atual;
    atualizarBotoes();
    $('estado-recepcao').textContent = 'Aguardando microfone e iniciando decodificador…';
    const pronto = await atual.iniciar();
    if (pronto && receptor === atual) $('estado-recepcao').textContent = 'Ouvindo. Aguardando um quadro FSK completo…';
  } catch (e) {
    if (!atual || receptor === atual) { erro(e); await pararRecepcao(); }
  }
});

$('autoteste').addEventListener('click', async () => {
  $('erro').textContent = '';
  testando = true;
  atualizarBotoes();
  $('estado-autoteste').classList.remove('falha');
  $('estado-autoteste').textContent = 'Codificando e decodificando…';
  try {
    await new Promise((resolve) => setTimeout(resolve, 0));
    const token = lerToken(), configuracao = lerConfiguracao();
    const sinal = codificarAmostras(token, 48000, configuracao);
    const tokens = new DecodificadorFsk(48000, configuracao).processar(sinal);
    if (tokens.length !== 1 || tokens[0] !== token) throw new Error('Autoteste não recuperou o token esperado.');
    $('estado-autoteste').textContent = `Autoteste aprovado: ${tokens[0]}, CRC válido. Nenhum som foi reproduzido.`;
  } catch (e) {
    erro(e);
    $('estado-autoteste').classList.add('falha');
    $('estado-autoteste').textContent = `Autoteste falhou: ${e.message ?? String(e)}`;
  }
  finally { testando = false; atualizarBotoes(); }
});

$('parar-emissao').addEventListener('click', () => { void pararEmissao(); });
$('parar-recepcao').addEventListener('click', () => { void pararRecepcao(); });
$('volume').addEventListener('input', () => { $('volume-valor').textContent = `${Math.round(Number($('volume').value) * 100)}%`; });
const encerrar = () => { void pararEmissao(); void pararRecepcao(); };
window.addEventListener('pagehide', encerrar);
document.addEventListener('visibilitychange', () => { if (document.hidden) encerrar(); });
