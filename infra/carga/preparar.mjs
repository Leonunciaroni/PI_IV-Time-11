// Autoria: Diogo Gonçalves Tonhosolo | RA: 25894007
import { parseArgs } from 'node:util';
import { randomUUID, randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { http, loginProfessor, ClienteProtocolo, escreverCsv } from './lib.mjs';

const { values } = parseArgs({ options: {
  salas: { type: 'string', default: '15' }, alunos: { type: 'string', default: '70' },
  canal: { type: 'string', default: 'QRCODE' }, url: { type: 'string', default: 'http://localhost:8088' },
} });
const salas = Number(values.salas), alunosPorSala = Number(values.alunos);
if (!Number.isInteger(salas) || salas < 1 || salas > 15 || !Number.isInteger(alunosPorSala) || alunosPorSala < 1 || alunosPorSala > 70) {
  throw new Error('Use de 1 a 15 salas e de 1 a 70 alunos por sala.');
}
if (!['QRCODE', 'SOM'].includes(values.canal)) throw new Error('Canal deve ser QRCODE ou SOM.');
const url = new URL(values.url);
if (!['http:', 'https:'].includes(url.protocol) || url.pathname !== '/' || url.search || url.hash || url.username || url.password) {
  throw new Error('Informe somente a origem HTTP(S), por exemplo http://localhost:8088.');
}
const pasta = dirname(fileURLToPath(import.meta.url));
const identificador = `${Date.now().toString(36)}-${randomUUID().slice(0, 8)}`;
const diretorio = resolve(pasta, 'execucoes', identificador);
const manifesto = { id: identificador, baseUrl: url.origin, canal: values.canal,
  salas, alunosPorSala, total: salas * alunosPorSala, pronto: false, sessoes: [] };
const salvar = () => writeFile(resolve(diretorio, 'manifesto.json'), JSON.stringify(manifesto, null, 2));
const tokenProfessor = await loginProfessor(url);
await mkdir(diretorio, { recursive: true });
await salvar();
console.error(`Preparação fora da medição: ${diretorio}`);
const turmas = [];
// Sequencial para não misturar provisionamento (BCrypt/REST) com a carga de check-in.
for (let i = 0; i < salas; i++) {
  const sala = await http(url, '/api/salas', { token: tokenProfessor, status: 201,
    corpo: { nome: `Carga ${identificador} ${i + 1}`, latitude: -22.8337, longitude: -47.0525, raioMetros: 30 } });
  const turma = await http(url, '/api/turmas', { token: tokenProfessor, status: 201,
    corpo: { nome: `Carga ${identificador} ${i + 1}`, disciplina: 'Teste de carga', horarioPadrao: 'Seg 19:00' } });
  const alunos = [];
  for (let j = 0; j < alunosPorSala; j++) {
    const email = `carga.${identificador}.${i}.${j}@demo.br`;
    const senha = randomBytes(18).toString('base64url');
    const usuario = await http(url, '/api/auth/registro', { status: 201, corpo: {
      nome: `Carga ${i + 1} Aluno ${j + 1}`, email, senha, papel: 'ALUNO', matricula: `C${identificador}${i}-${j}`,
    } });
    const login = await http(url, '/api/auth/login', { corpo: { email, senha } });
    alunos.push({ alunoId: usuario.id, jwt: login.token, dispositivoId: randomUUID() });
  }
  await http(url, `/api/turmas/${turma.id}/matriculas`, { token: tokenProfessor,
    corpo: { alunoIds: alunos.map((a) => a.alunoId) } });
  turmas.push({ turmaId: turma.id, salaId: sala.id, alunos });
  console.error(`Sala ${i + 1}/${salas}: ${alunos.length} alunos preparados.`);
}
// Sessões são abertas só depois do provisionamento, com segredos novos por execução.
const professor = await ClienteProtocolo.conectar(url, tokenProfessor);
const linhas = [];
try {
  for (const turma of turmas) {
    const { dados } = await professor.pedir('sessao.abrir', {
      turmaId: turma.turmaId, salaId: turma.salaId, modo: values.canal,
    }, 'sessao.aberta');
    manifesto.sessoes.push({ turmaId: turma.turmaId, salaId: turma.salaId, sessaoId: dados.sessaoId,
      alunos: turma.alunos.map((a) => a.alunoId) });
    await salvar();
    for (const aluno of turma.alunos) linhas.push({ ...aluno, sessaoId: dados.sessaoId,
      canal: values.canal, ...dados.totp, latitude: -22.8337, longitude: -47.0525, precisaoMetros: 0 });
  }
} finally { professor.fechar(); }
await writeFile(resolve(diretorio, 'alunos.csv'), escreverCsv(linhas));
// Properties do Java: caminhos com / e caracteres não ASCII escapados.
const propriedade = (valor) => String(valor).replaceAll('\\', '/').replace(/[^\x20-\x7e]/g,
  (c) => `\\u${c.charCodeAt(0).toString(16).padStart(4, '0')}`);
const propriedades = { base_url: url.origin, threads: manifesto.total, ramp_up: 10, barreira_timeout: 30000,
  dados: resolve(diretorio, 'alunos.csv'), script_ws: resolve(pasta, 'ws.groovy'),
  sample_variables: 'sessaoId,alunoId',
  'jmeter.save.saveservice.output_format': 'csv', 'jmeter.save.saveservice.print_field_names': true,
  'jmeter.save.saveservice.timestamp_format': 'ms', 'jmeter.save.saveservice.default_delimiter': ',',
  'jmeter.save.saveservice.response_data': false, 'jmeter.save.saveservice.response_data.on_error': false,
  'jmeter.save.saveservice.samplerData': false, 'jmeter.save.saveservice.requestHeaders': false,
  'jmeter.save.saveservice.responseHeaders': false,
};
await writeFile(resolve(diretorio, 'carga.properties'), Object.entries(propriedades)
  .map(([chave, valor]) => `${chave}=${propriedade(valor)}`).join('\n') + '\n');
manifesto.pronto = true;
await salvar();
console.log(`Dados prontos: ${diretorio}`);
console.log('Execute o JMeter uma única vez com carga.properties desta pasta. Para repetir, prepare outra execução.');
