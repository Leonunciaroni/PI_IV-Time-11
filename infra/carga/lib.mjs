import { randomUUID } from 'node:crypto';

export async function http(base, caminho, { corpo, token, status = 200 } = {}) {
  const resposta = await fetch(new URL(caminho, base), {
    method: corpo === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
    signal: AbortSignal.timeout(15000),
  });
  if (resposta.status !== status) throw new Error(`${caminho}: HTTP ${resposta.status}, esperado ${status}.`);
  return resposta.json();
}

export async function loginProfessor(base) {
  const login = await http(base, '/api/auth/login', { corpo: {
    email: process.env.PROFESSOR_EMAIL ?? 'professor@demo.br',
    senha: process.env.PROFESSOR_SENHA ?? 'demo12345',
  } });
  if (login.usuario?.papel !== 'PROFESSOR') throw new Error('A conta de preparação deve ser de professor.');
  return login.token;
}

/** Cliente de preparação/conferência. Não participa da janela medida pelo JMeter. */
export class ClienteProtocolo {
  constructor(url) {
    this.pendentes = new Map();
    this.socket = new WebSocket(url);
    this.socket.addEventListener('message', ({ data }) => {
      try {
        const mensagem = JSON.parse(data);
        if (mensagem.v !== 1) throw new Error('Versão de protocolo inesperada.');
        const pedido = this.pendentes.get(mensagem.ref);
        if (!pedido) {
          if (mensagem.tipo === 'erro') throw new Error(`Erro sem correlação: ${mensagem.dados?.codigo}`);
          return; // Eventos do painel podem chegar antes da resposta correlacionada.
        }
        if (mensagem.tipo !== pedido.esperado) {
          pedido.rejeitar(new Error(`Esperado ${pedido.esperado}; recebido ${mensagem.tipo} (${mensagem.dados?.codigo ?? ''}).`));
        } else pedido.resolver(mensagem);
      } catch (erro) { this.falhar(erro); }
    });
    this.socket.addEventListener('close', () => this.falhar(new Error('WebSocket fechado.')));
    this.socket.addEventListener('error', () => this.falhar(new Error('Falha de transporte WebSocket.')));
  }

  static async conectar(base, token) {
    const url = new URL('/ws', base);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    const cliente = new ClienteProtocolo(url);
    try {
      await new Promise((resolve, reject) => {
        const limite = setTimeout(() => reject(new Error('Timeout ao conectar WebSocket.')), 5000);
        cliente.socket.addEventListener('open', () => { clearTimeout(limite); resolve(); }, { once: true });
        cliente.socket.addEventListener('error', () => { clearTimeout(limite); reject(new Error('Falha ao conectar WebSocket.')); }, { once: true });
      });
      await cliente.pedir('auth', { token }, 'auth.ok');
      return cliente;
    } catch (erro) { cliente.fechar(); throw erro; }
  }

  pedir(tipo, dados, esperado) {
    const id = randomUUID();
    return new Promise((resolver, rejeitar) => {
      const limpar = (retorno, valor) => { clearTimeout(limite); this.pendentes.delete(id); retorno(valor); };
      const limite = setTimeout(() => limpar(rejeitar, new Error(`Timeout em ${tipo}.`)), 5000);
      this.pendentes.set(id, { esperado,
        resolver: (m) => limpar(resolver, m), rejeitar: (e) => limpar(rejeitar, e) });
      try { this.socket.send(JSON.stringify({ v: 1, tipo, id, ts: Date.now(), dados })); }
      catch (erro) { limpar(rejeitar, erro); }
    });
  }

  falhar(erro) { for (const pedido of this.pendentes.values()) pedido.rejeitar(erro); }
  fechar() { this.falhar(new Error('Cliente encerrado.')); this.socket.close(); }
}

/** CSV RFC 4180, incluindo aspas duplicadas e quebras de linha dentro de campos. */
export function lerCsv(texto) {
  const linhas = [];
  let linha = [], campo = '', aspas = false;
  texto = texto.replace(/^\uFEFF/, '');
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (c === '"') {
      if (aspas && texto[i + 1] === '"') { campo += '"'; i++; }
      else aspas = !aspas;
    } else if (!aspas && (c === ',' || c === '\n' || c === '\r')) {
      linha.push(campo); campo = '';
      if (c !== ',') {
        if (c === '\r' && texto[i + 1] === '\n') i++;
        linhas.push(linha); linha = [];
      }
    } else campo += c;
  }
  if (aspas) throw new Error('CSV com aspas não fechadas.');
  if (campo || linha.length) { linha.push(campo); linhas.push(linha); }
  const cabecalho = linhas.shift();
  if (!cabecalho?.length) throw new Error('CSV vazio.');
  return linhas.filter((l) => l.some(Boolean)).map((l) => {
    if (l.length !== cabecalho.length) throw new Error('Número de colunas inválido no CSV.');
    return Object.fromEntries(cabecalho.map((chave, i) => [chave, l[i]]));
  });
}

export function escreverCsv(linhas) {
  const campos = Object.keys(linhas[0]);
  const escapar = (valor) => `"${String(valor).replaceAll('"', '""')}"`;
  return [campos.join(','), ...linhas.map((linha) => campos.map((c) => escapar(linha[c])).join(','))].join('\n') + '\n';
}
