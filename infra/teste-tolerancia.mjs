/*
 * Autores:
 * - Leonardo Miranda Nunciaroni (RA 25002726)
 * - João Pedro Panza Mainieri (RA 25006642)
 */
// Cenários de tolerância a falha (infra/README.md, seção "Tolerância a falha").
//
// Uso, com o Compose rodando e as réplicas escaladas (ver o README):
//   node infra/teste-tolerancia.mjs sessao   # derruba uma réplica do servidor de sessão durante a chamada
//   node infra/teste-tolerancia.mjs api      # derruba uma réplica da API durante leituras REST
//   node infra/teste-tolerancia.mjs mongo    # derruba o primário do MongoDB durante marcações de presença
//
// Requer Node 22+ (WebSocket e fetch globais) e o Docker CLI. Cada execução cria uma turma, alunos
// e uma sessão CLASSICA próprios, identificados por um sufixo. Os containers são derrubados e
// religados com `docker stop` / `docker start`. Imprime um resumo em JSON no stdout.
// Variáveis: BASE_URL (padrão http://localhost:8088) e ALUNOS (padrão 20).
// Antes, rode ./usuarios-demo.sh para criar o professor de demonstração.

import { execFile } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { promisify } from 'node:util';

const executar = promisify(execFile);
const BASE = process.env.BASE_URL ?? 'http://localhost:8088';
const WS_URL = `${BASE.replace(/^http/, 'ws')}/ws`;
const SENHA = 'demo12345';
const PROFESSOR = 'professor@demo.br';
const ALUNOS = Number(process.env.ALUNOS ?? 20);
const PROJETO = 'fast-attendance';

const agora = () => Date.now();
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const log = (...partes) => console.error(new Date().toISOString(), ...partes);

/** Chama o Docker sem bloquear o event loop: as marcações continuam rodando durante a parada. */
async function docker(...args) {
  const { stdout } = await executar('docker', args, { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  return stdout.trim();
}

/** Containers em execução de um serviço do Compose, em ordem de nome. */
async function emExecucao(servico) {
  const saida = await docker('ps', '--filter', `label=com.docker.compose.project=${PROJETO}`,
    '--filter', `label=com.docker.compose.service=${servico}`, '--format', '{{.Names}}');
  return saida.split('\n').filter(Boolean).sort();
}

/** Para o container com SIGTERM (10 s de tolerância antes do SIGKILL); devolve o tempo gasto em ms. */
async function parar(container) {
  const inicio = agora();
  await docker('stop', '-t', '10', container);
  return agora() - inicio;
}

async function iniciar(container) {
  await docker('start', container);
}

async function esperarAte(condicao, limiteMs) {
  const fim = agora() + limiteMs;
  while (agora() < fim) {
    if (condicao()) return true;
    await sleep(100);
  }
  return condicao();
}

async function http(metodo, caminho, corpo, token) {
  const resposta = await fetch(BASE + caminho, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: corpo ? JSON.stringify(corpo) : undefined,
    signal: AbortSignal.timeout(5000),
  });
  const texto = await resposta.text();
  let dados = null;
  try {
    dados = texto ? JSON.parse(texto) : null;
  } catch {
    dados = texto;
  }
  return { status: resposta.status, dados };
}

/**
 * Cliente do protocolo (docs/protocolo-v1.md) com reconexão. Ao receber um fechamento, espera com
 * backoff (0,5 s, 1 s, 2 s... até 8 s) e reconecta, enviando auth de novo. Registra cada fechamento
 * com o código e cada reconexão com o tempo entre o fechamento e o auth.ok.
 */
class Cliente {
  constructor(nome, token) {
    this.nome = nome;
    this.token = token;
    this.ws = null;
    this.pendentes = new Map();
    this.eventos = [];
    this.iniciado = false;
    this.reconectando = false;
    this.autenticado = false;
    this.parado = false;
    this.timerPing = null;
  }

  async conectar() {
    const ws = new WebSocket(WS_URL);
    this.ws = ws;
    ws.onmessage = (evento) => this.receber(evento.data);
    ws.onclose = (evento) => this.fechou(ws, evento.code, evento.reason);
    await new Promise((ok, falha) => {
      ws.onopen = () => ok();
      ws.onerror = () => falha(new Error('falha ao abrir o WebSocket'));
    });
    const resposta = await this.enviar('auth', { token: this.token });
    if (resposta.tipo !== 'auth.ok') {
      throw new Error(`auth recusada: ${resposta.tipo} ${JSON.stringify(resposta.dados)}`);
    }
    this.autenticado = true;
    this.iniciado = true;
    if (!this.timerPing) {
      // Keep-alive do protocolo: ping a cada 15 s (o servidor fecha conexões sem tráfego por 60 s)
      this.timerPing = setInterval(() => {
        if (this.ws?.readyState === WebSocket.OPEN) {
          this.ws.send(JSON.stringify({ v: 1, tipo: 'ping', id: randomUUID().slice(0, 8), ts: agora(), dados: {} }));
        }
      }, 15000);
    }
  }

  receber(texto) {
    const mensagem = JSON.parse(texto);
    if (mensagem.ref && this.pendentes.has(mensagem.ref)) {
      const pendente = this.pendentes.get(mensagem.ref);
      this.pendentes.delete(mensagem.ref);
      pendente.resolver(mensagem);
    } else if (!mensagem.ref) {
      this.eventos.push({ t: agora(), tipo: 'evento', nome: mensagem.tipo });
    }
  }

  enviar(tipo, dados = {}) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error('sem conexão'));
    }
    const id = randomUUID().slice(0, 12);
    return new Promise((resolver, rejeitar) => {
      const limite = setTimeout(() => {
        this.pendentes.delete(id);
        rejeitar(new Error(`sem resposta a ${tipo} em 8 s`));
      }, 8000);
      this.pendentes.set(id, {
        resolver: (mensagem) => { clearTimeout(limite); resolver(mensagem); },
        rejeitar: (erro) => { clearTimeout(limite); rejeitar(erro); },
      });
      this.ws.send(JSON.stringify({ v: 1, tipo, id, ts: agora(), dados }));
    });
  }

  fechou(ws, codigo, motivo) {
    if (ws !== this.ws) return;
    this.autenticado = false;
    for (const pendente of this.pendentes.values()) pendente.rejeitar(new Error('conexão fechada'));
    this.pendentes.clear();
    this.fechadoEm = agora();
    this.eventos.push({ t: agora(), tipo: 'fechado', codigo, motivo: motivo });
    if (!this.parado && this.iniciado && !this.reconectando) this.reconectar();
  }

  async reconectar() {
    this.reconectando = true;
    let espera = 500;
    while (!this.parado) {
      try {
        await this.conectar();
        this.eventos.push({ t: agora(), tipo: 'reconectado', apos: agora() - this.fechadoEm });
        break;
      } catch {
        await sleep(espera);
        espera = Math.min(espera * 2, 8000);
      }
    }
    this.reconectando = false;
  }

  fechar() {
    this.parado = true;
    clearInterval(this.timerPing);
    this.ws?.close();
  }
}

async function preparar(quantidadeAlunos) {
  const sufixo = Date.now().toString(36);
  const login = await http('POST', '/api/auth/login', { email: PROFESSOR, senha: SENHA });
  if (login.status !== 200) throw new Error(`login do professor: HTTP ${login.status} (rode ./usuarios-demo.sh)`);
  const tokenProfessor = login.dados.token;

  const turma = await http('POST', '/api/turmas',
    { nome: `Tolerancia ${sufixo}`, disciplina: 'Teste de falha', horarioPadrao: 'seg 08:00' }, tokenProfessor);
  if (turma.status !== 201) throw new Error(`criar turma: HTTP ${turma.status}`);

  const alunos = [];
  for (let i = 0; i < quantidadeAlunos; i++) {
    const registro = await http('POST', '/api/auth/registro', {
      nome: `Aluno Tolerancia ${i}`,
      email: `tol${sufixo}${i}@demo.br`,
      senha: SENHA,
      papel: 'ALUNO',
      matricula: `T${sufixo}${i}`,
    });
    if (registro.status !== 201) throw new Error(`registrar aluno ${i}: HTTP ${registro.status}`);
    const login = await http('POST', '/api/auth/login', { email: `tol${sufixo}${i}@demo.br`, senha: SENHA });
    if (login.status !== 200) throw new Error(`login do aluno ${i}: HTTP ${login.status}`);
    alunos.push({ id: registro.dados.id, token: login.dados.token });
  }

  const matricula = await http('POST', `/api/turmas/${turma.dados.id}/matriculas`,
    { alunoIds: alunos.map((a) => a.id) }, tokenProfessor);
  if (matricula.status !== 200) throw new Error(`matricular: HTTP ${matricula.status}`);

  return { turmaId: turma.dados.id, tokenProfessor, alunos };
}

async function abrirSessao(professor, turmaId) {
  const resposta = await professor.enviar('sessao.abrir', { turmaId, modo: 'CLASSICA' });
  if (resposta.tipo !== 'sessao.aberta') {
    throw new Error(`sessao.abrir: ${resposta.tipo} ${JSON.stringify(resposta.dados)}`);
  }
  return resposta.dados.sessaoId;
}

/** Marca presença de alunos em sequência até o prazo; cada resposta vira um registro. */
function marcarAteO(prof, sessaoId, alunos, registros, prazo) {
  return (async () => {
    let i = 0;
    while (agora() < prazo) {
      const aluno = alunos[i++ % alunos.length];
      const inicio = agora();
      try {
        const resposta = await prof.enviar('presenca.marcar', { sessaoId, alunoId: aluno.id, presente: true });
        const ok = resposta.tipo === 'presenca.atualizada';
        registros.push({ t: inicio, fim: agora(), ok, alunoId: aluno.id, erro: ok ? null : resposta.dados?.codigo });
      } catch (erro) {
        registros.push({ t: inicio, fim: agora(), ok: false, alunoId: aluno.id, erro: erro.message });
      }
      await sleep(250); // 4 mensagens por segundo: abaixo do limite de 60 em 10 s (LimitadorDeMensagens)
    }
  })();
}

function resumoMarcacoes(registros, tParada) {
  const confirmadas = registros.filter((r) => r.ok);
  const falhas = registros.filter((r) => !r.ok);
  const porErro = {};
  for (const falha of falhas) porErro[falha.erro] = (porErro[falha.erro] ?? 0) + 1;
  let maiorIntervaloMs = 0;
  for (let k = 1; k < confirmadas.length; k++) {
    maiorIntervaloMs = Math.max(maiorIntervaloMs, confirmadas[k].t - confirmadas[k - 1].t);
  }
  const primeiraAposParada = tParada ? confirmadas.find((r) => r.t >= tParada) : undefined;
  return {
    tentativas: registros.length,
    confirmadas: confirmadas.length,
    falhas: falhas.length,
    errosPorCodigo: porErro,
    maiorIntervaloSemConfirmacaoMs: maiorIntervaloMs,
    primeiraConfirmacaoAposParadaMs: primeiraAposParada ? primeiraAposParada.t - tParada : null,
  };
}

function resumoCliente(cliente, tParada) {
  const fechamentos = cliente.eventos.filter((e) => e.tipo === 'fechado');
  const reconexoes = cliente.eventos.filter((e) => e.tipo === 'reconectado');
  return {
    nome: cliente.nome,
    fechamentos: fechamentos.map((e) => ({ codigo: e.codigo, motivo: e.motivo, apos_parada_ms: e.t - tParada })),
    reconexoesMs: reconexoes.map((e) => e.apos),
    autenticadoNoFim: cliente.autenticado,
  };
}

async function cenarioSessao() {
  const replicas = await emExecucao('sessao');
  if (replicas.length < 2) {
    throw new Error('são necessárias 2 réplicas de sessao: docker compose up -d --scale sessao=2');
  }
  const alvo = replicas[0];
  const dados = await preparar(ALUNOS);
  const professor = new Cliente('professor', dados.tokenProfessor);
  await professor.conectar();
  const sessaoId = await abrirSessao(professor, dados.turmaId);
  const alunos = [];
  for (const [i, aluno] of dados.alunos.entries()) {
    const cliente = new Cliente(`aluno${i}`, aluno.token);
    await cliente.conectar();
    alunos.push(cliente);
  }
  log(`sessao ${sessaoId}; ${alunos.length} alunos conectados; réplicas: ${replicas.join(', ')}; alvo: ${alvo}`);

  const registros = [];
  const marcacoes = marcarAteO(professor, sessaoId, dados.alunos, registros, agora() + 40000);
  await sleep(8000);
  const tParada = agora();
  log(`parando ${alvo} com SIGTERM`);
  const duracaoParada = await parar(alvo);
  await marcacoes;
  const todosReconectados = await esperarAte(() => [professor, ...alunos].every((c) => c.autenticado), 30000);
  const resultado = {
    cenario: 'sessao',
    replicaParada: alvo,
    tempoDocker_stop_ms: duracaoParada,
    clientesNaoReconectadosNoPrazo: todosReconectados ? 0 : [professor, ...alunos].filter((c) => !c.autenticado).length,
    professor: resumoCliente(professor, tParada),
    alunos: alunos.map((c) => resumoCliente(c, tParada)),
    presencasDoProfessor: resumoMarcacoes(registros, tParada),
  };
  for (const cliente of [professor, ...alunos]) cliente.fechar();
  await iniciar(alvo);
  resultado.replicaReiniciada = alvo;
  return resultado;
}

async function cenarioApi() {
  const replicas = await emExecucao('api');
  if (replicas.length < 2) {
    throw new Error('são necessárias 2 réplicas de api: docker compose up -d --scale api=2');
  }
  const alvo = replicas[0];
  const dados = await preparar(ALUNOS);
  const professor = new Cliente('professor', dados.tokenProfessor);
  await professor.conectar();
  const sessaoId = await abrirSessao(professor, dados.turmaId);
  const alunos = [];
  for (const [i, aluno] of dados.alunos.slice(0, 5).entries()) {
    const cliente = new Cliente(`aluno${i}`, aluno.token);
    await cliente.conectar();
    alunos.push(cliente);
  }
  log(`sessao ${sessaoId}; réplicas da API: ${replicas.join(', ')}; alvo: ${alvo}`);

  const requisicoes = [];
  const fim = agora() + 30000;
  const leituras = (async () => {
    while (agora() < fim) {
      const inicio = agora();
      try {
        const resposta = await http('GET', '/api/auth/me', null, dados.tokenProfessor);
        requisicoes.push({ t: inicio, status: resposta.status, ms: agora() - inicio });
      } catch (erro) {
        requisicoes.push({ t: inicio, status: erro.name, ms: agora() - inicio });
      }
      await sleep(100);
    }
  })();

  await sleep(5000);
  const tParada = agora();
  log(`parando ${alvo} com SIGTERM`);
  const duracaoParada = await parar(alvo);
  await sleep(10000);
  await iniciar(alvo);
  log(`${alvo} reiniciada`);
  await leituras;

  const falhas = requisicoes.filter((r) => r.status !== 200);
  const resultado = {
    cenario: 'api',
    replicaParada: alvo,
    tempoDocker_stop_ms: duracaoParada,
    leituras: { total: requisicoes.length, ok: requisicoes.length - falhas.length, falhas: falhas.length,
      statusDasFalhas: [...new Set(falhas.map((f) => f.status))],
      primeiraFalhaAposParadaMs: falhas[0] ? falhas[0].t - tParada : null,
      ultimaFalhaAposParadaMs: falhas.length ? falhas.at(-1).t - tParada : null },
    alunosConectadosDuranteOTeste: alunos.map((c) => resumoCliente(c, tParada)),
    professorAutenticadoNoFim: professor.autenticado,
  };
  for (const cliente of [professor, ...alunos]) cliente.fechar();
  return resultado;
}

async function ehPrimario(container) {
  try {
    const saida = await docker('exec', container, 'mongosh', '--quiet', '--eval', 'db.hello().isWritablePrimary');
    return saida.trim() === 'true';
  } catch {
    return false;
  }
}

async function primarioAtual(containers) {
  for (const container of containers) {
    if (await ehPrimario(container)) return container;
  }
  return null;
}

async function cenarioMongo() {
  const emPe = (await Promise.all(["mongo1", "mongo2", "mongo3"].map(emExecucao))).flat();
  const alvo = await primarioAtual(emPe);
  if (!alvo) throw new Error('nenhum primário encontrado entre os containers em execução');

  const dados = await preparar(ALUNOS);
  const professor = new Cliente('professor', dados.tokenProfessor);
  await professor.conectar();
  const sessaoId = await abrirSessao(professor, dados.turmaId);
  log(`sessao ${sessaoId}; primário atual: ${alvo}`);

  const registros = [];
  const marcacoes = marcarAteO(professor, sessaoId, dados.alunos, registros, agora() + 40000);
  await sleep(8000);
  const tParada = agora();
  log(`parando o primário ${alvo} com SIGTERM`);
  const duracaoParada = await parar(alvo);
  await marcacoes;
  const novoPrimario = await primarioAtual(emPe.filter((m) => m !== alvo));

  const confirmados = [...new Set(registros.filter((r) => r.ok).map((r) => r.alunoId))];
  const consulta = `JSON.stringify(db.getSiblingDB("fast_attendance").presencas.find({ sessaoId: "${sessaoId}", presente: true }, { alunoId: 1, _id: 0 }).toArray().map(d => d.alunoId))`;
  // Com autenticação (#54): o usuário da aplicação vem das variáveis do próprio container
  const gravados = JSON.parse(await docker("exec", novoPrimario, "sh", "-c",
    'mongosh "mongodb://$MONGO_APP_USUARIO:$MONGO_APP_SENHA@localhost:27017/fast_attendance?replicaSet=rs0" --quiet --eval "$1"',
    'sh', consulta));
  const gravadosSet = new Set(gravados);
  const perdidos = confirmados.filter((id) => !gravadosSet.has(id));

  const resultado = {
    cenario: 'mongo',
    primarioParado: alvo,
    novoPrimario,
    tempoDocker_stop_ms: duracaoParada,
    presencas: resumoMarcacoes(registros, tParada),
    alunosComPresencaConfirmada: confirmados.length,
    presencasGravadasNoBanco: gravadosSet.size,
    presencasConfirmadasPerdidas: perdidos.length,
    sessaoSobreviveu: professor.autenticado,
  };
  professor.fechar();
  await iniciar(alvo);
  resultado.primarioReiniciado = alvo;
  return resultado;
}

const cenarios = { sessao: cenarioSessao, api: cenarioApi, mongo: cenarioMongo };
const escolhido = process.argv[2];
if (!cenarios[escolhido]) {
  console.error('uso: node infra/teste-tolerancia.mjs <sessao|api|mongo>');
  process.exit(2);
}
try {
  const resultado = await cenarios[escolhido]();
  console.log(JSON.stringify(resultado, null, 2));
  process.exit(0);
} catch (erro) {
  console.error('falha no cenário:', erro.message);
  process.exit(1);
}
