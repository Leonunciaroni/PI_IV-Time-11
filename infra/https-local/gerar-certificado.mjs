// Gera o certificado autoassinado do HTTPS local (serviço `nginx-https` do Compose), que serve de
// reserva quando o túnel da Cloudflare não conecta (rede que bloqueia a porta 7844).
//
// Uso (Node 22+; precisa de `openssl` no PATH ou do Docker aberto):
//   node infra/https-local/gerar-certificado.mjs
//   cd infra && docker compose --profile https-local up -d nginx-https
//
// O certificado vale para `localhost`, 127.0.0.1 e todos os IPv4 que o computador tem agora. Ao
// trocar de rede o IP muda: rode o script de novo e reinicie o serviço
// (`docker compose --profile https-local restart nginx-https`).
//
// Autoassinado: o navegador do celular mostra um aviso, que é preciso aceitar uma vez. Câmera e
// microfone só funcionam depois disso. Notebook e celular precisam estar na mesma rede, sem
// isolamento de clientes, e o firewall do Windows precisa deixar a porta de entrada.

import { execFileSync } from 'node:child_process';
import { mkdirSync, existsSync } from 'node:fs';
import { networkInterfaces } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DIAS = 30;
const PORTA = process.env.HTTPS_LOCAL_PORTA ?? '8443';
const pasta = join(dirname(fileURLToPath(import.meta.url)), 'certs');

// Interfaces de máquina virtual (WSL, Hyper-V, Docker...): o celular não chega por elas
const VIRTUAL = /vEthernet|WSL|Hyper-V|Docker|VirtualBox|VMware|vboxnet|^docker|^br-|^veth/i;

function ipsDaMaquina() {
  const ips = [];
  for (const [nome, enderecos] of Object.entries(networkInterfaces())) {
    if (VIRTUAL.test(nome)) continue;
    for (const e of enderecos ?? []) {
      // Sem loopback e sem endereço de link local (169.254.x.x), que o celular não usa
      if (e.family === 'IPv4' && !e.internal && !e.address.startsWith('169.254.')) {
        ips.push({ nome, ip: e.address });
      }
    }
  }
  return ips;
}

function temOpenssl() {
  try {
    execFileSync('openssl', ['version'], { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

function argumentosOpenssl(saida, san) {
  return [
    'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-sha256', '-days', String(DIAS),
    '-keyout', `${saida}/key.pem`, '-out', `${saida}/cert.pem`,
    '-subj', '/CN=Fast Attendance (local)',
    '-addext', `subjectAltName=${san}`,
    '-addext', 'keyUsage=digitalSignature,keyEncipherment',
    '-addext', 'extendedKeyUsage=serverAuth',
  ];
}

const ips = ipsDaMaquina();
const san = ['DNS:localhost', 'IP:127.0.0.1', ...ips.map(({ ip }) => `IP:${ip}`)].join(',');
mkdirSync(pasta, { recursive: true });

if (temOpenssl()) {
  execFileSync('openssl', argumentosOpenssl(pasta, san), { stdio: 'ignore' });
} else {
  // Sem openssl no computador: usa a imagem do MongoDB, que o Compose já baixa e tem openssl
  console.log('openssl não encontrado no PATH; usando a imagem mongo:8.0 pelo Docker.');
  const usuario = typeof process.getuid === 'function' ? ['--user', `${process.getuid()}:${process.getgid()}`] : [];
  execFileSync(
    'docker',
    ['run', '--rm', ...usuario, '-v', `${pasta}:/saida`, '--entrypoint', 'openssl', 'mongo:8.0', ...argumentosOpenssl('/saida', san)],
    { stdio: 'inherit' },
  );
}

if (!existsSync(join(pasta, 'cert.pem')) || !existsSync(join(pasta, 'key.pem'))) {
  console.error('Falhou: cert.pem e key.pem não foram criados em', pasta);
  process.exit(1);
}

console.log(`Certificado gerado em ${pasta} (válido por ${DIAS} dias).\n`);
console.log('Endereços para abrir no celular (use o IP da rede em que ele está):');
if (ips.length === 0) {
  console.log('  nenhum IPv4 de rede encontrado: conecte o computador a uma rede e rode de novo.');
}
for (const { nome, ip } of ips) {
  console.log(`  https://${ip}:${PORTA}/aluno           (${nome})`);
  console.log(`  https://${ip}:${PORTA}/teste-celular/  (diagnóstico de câmera, microfone e localização)`);
}
console.log('\nPróximo passo: cd infra && docker compose --profile https-local up -d nginx-https');
