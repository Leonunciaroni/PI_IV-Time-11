// Autoria: Diogo Gonçalves Tonhosolo | RA: 25894007
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { lerCsv, loginProfessor, ClienteProtocolo } from './lib.mjs';
import { resumir } from './metricas.mjs';

if (!process.argv[2]) throw new Error('Uso: node infra/carga/analisar.mjs <pasta-da-execucao>');
const pasta = resolve(process.argv[2]);
const manifesto = JSON.parse(await readFile(resolve(pasta, 'manifesto.json'), 'utf8'));
if (!manifesto.pronto) throw new Error('Preparação não concluída.');
const amostras = lerCsv(await readFile(resolve(pasta, 'resultados.jtl'), 'utf8'));
const token = await loginProfessor(manifesto.baseUrl);
const professor = await ClienteProtocolo.conectar(manifesto.baseUrl, token);
const estados = [];
try {
  for (const sessao of manifesto.sessoes) {
    const { dados } = await professor.pedir('sessao.acompanhar', { sessaoId: sessao.sessaoId }, 'sessao.estado');
    if (dados.sessaoId !== sessao.sessaoId) throw new Error('Sessão devolvida difere da solicitada.');
    estados.push(dados);
  }
} finally { professor.fechar(); }
// sessao.estado consulta as presenças no MongoDB; não usa o contador de eventos recebidos.
await writeFile(resolve(pasta, 'persistencia.json'), JSON.stringify(estados, null, 2));
const relatorio = resumir(manifesto, amostras, estados);
await writeFile(resolve(pasta, 'relatorio.json'), JSON.stringify(relatorio, null, 2));
console.log(JSON.stringify({ criteriosAtendidos: relatorio.criteriosAtendidos, cargaCompleta: relatorio.cargaCompleta,
  esperados: relatorio.esperados, auth: relatorio.auth, checkin: relatorio.checkin,
  gravados: relatorio.persistencia.gravados, perdidos: relatorio.persistencia.perdidos.length,
  problemas: relatorio.problemas, relatorio: resolve(pasta, 'relatorio.json') }, null, 2));
if (!relatorio.criteriosAtendidos) process.exitCode = 1;
