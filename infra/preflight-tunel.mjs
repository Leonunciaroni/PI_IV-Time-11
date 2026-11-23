// Pré-voo do túnel HTTPS (docs/https-celulares.md): confere, a partir deste computador, se a rede
// atual deixa o Cloudflare Quick Tunnel funcionar. Não sobe nem derruba nada e não publica nada.
//
// Uso (Node 22+, sem Docker):
//   node infra/preflight-tunel.mjs
//
// O túnel precisa de três coisas da rede: resolver o DNS da Cloudflare, abrir conexão de saída
// TCP na porta 7844 com handshake TLS completo (é por ela que o cloudflared fala, com HTTP/2) e
// alcançar api.trycloudflare.com na 443 (é onde o endereço temporário é pedido).
// Sai com código 0 se tudo passou e 1 se algo falhou.
//
// Limite: o teste roda no Windows/macOS/Linux, não dentro do Docker. Se ele passar e o túnel ainda
// falhar logo depois de trocar de rede, reinicie o Docker Desktop para ele pegar a rede nova.

import { lookup } from 'node:dns/promises';
import { connect as conectarTls } from 'node:tls';

const BORDAS = ['region1.v2.argotunnel.com', 'region2.v2.argotunnel.com'];
const PORTA_TUNEL = 7844;
const TEMPO_MS = 8000;

const resultados = [];

function registrar(nome, ok, detalhe) {
  resultados.push({ nome, ok });
  console.log(`${ok ? 'OK    ' : 'FALHOU'}  ${nome}${detalhe ? ` — ${detalhe}` : ''}`);
}

async function resolverDns(host) {
  try {
    const { address } = await lookup(host);
    registrar(`DNS ${host}`, true, address);
    return true;
  } catch (e) {
    registrar(`DNS ${host}`, false, e.code ?? e.message);
    return false;
  }
}

// Abre TCP e faz o handshake TLS: o bloqueio em 7844 aparece como timeout, e um filtro que corta
// a conexão no meio (visto em hotspot) aparece como EOF/ECONNRESET.
// O certificado não é verificado: a borda do Cloudflare usa uma CA própria que o Node não conhece, e
// aqui só interessa saber se a conexão chega até lá. Nenhum dado é enviado.
function handshakeTls(host, porta) {
  return new Promise((resolver) => {
    const inicio = Date.now();
    const socket = conectarTls({
      host,
      port: porta,
      servername: host,
      ALPNProtocols: ['h2'],
      rejectUnauthorized: false,
    });
    let encerrado = false;
    const terminar = (ok, detalhe) => {
      if (encerrado) return;
      encerrado = true;
      socket.destroy();
      resolver({ ok, detalhe });
    };
    socket.setTimeout(TEMPO_MS, () => terminar(false, `sem resposta em ${TEMPO_MS / 1000} s (timeout)`));
    socket.on('secureConnect', () => terminar(true, `${Date.now() - inicio} ms`));
    socket.on('error', (e) => terminar(false, e.code ?? e.message));
    socket.on('close', () => terminar(false, 'conexão fechada durante o handshake (EOF)'));
  });
}

async function pedirEnderecoTemporario() {
  try {
    // GET não cria túnel (a API só aceita POST e responde 405); basta chegar até ela com TLS válido.
    const resposta = await fetch('https://api.trycloudflare.com/tunnel', {
      signal: AbortSignal.timeout(TEMPO_MS),
    });
    registrar('HTTPS api.trycloudflare.com:443', true, `HTTP ${resposta.status}`);
  } catch (e) {
    const causa = e.cause?.code ?? e.cause?.message ?? e.name;
    registrar('HTTPS api.trycloudflare.com:443', false, causa);
  }
}

console.log('Pré-voo do túnel HTTPS: testando a rede deste computador\n');

for (const host of BORDAS) {
  if (!(await resolverDns(host))) continue;
  const { ok, detalhe } = await handshakeTls(host, PORTA_TUNEL);
  registrar(`TLS ${host}:${PORTA_TUNEL}`, ok, detalhe);
}
await pedirEnderecoTemporario();

const tunelPorta = resultados.filter((r) => r.nome.startsWith('TLS ')).some((r) => r.ok);
const api = resultados.find((r) => r.nome.startsWith('HTTPS '))?.ok;
console.log();
if (tunelPorta && api) {
  console.log('Resultado: esta rede deixa o túnel funcionar.');
} else if (!tunelPorta) {
  console.log(
    `Resultado: a porta ${PORTA_TUNEL} está bloqueada ou o handshake é cortado. O túnel não vai conectar` +
      ' nesta rede (timeout = firewall; EOF = filtro que corta a conexão). Troque de rede e rode de novo.',
  );
} else {
  console.log('Resultado: a API do endereço temporário está inacessível, então o túnel não gera URL.');
}
process.exit(tunelPorta && api ? 0 : 1);
