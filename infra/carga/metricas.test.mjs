import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resumir } from './metricas.mjs';
import { lerCsv, escreverCsv } from './lib.mjs';

function cenario() {
  const manifesto = { id: 'teste', pronto: true, canal: 'QRCODE', salas: 1, alunosPorSala: 2, total: 2,
    sessoes: [{ sessaoId: 'sessao', alunos: ['ana', 'bruno'] }] };
  const amostras = ['auth', 'checkin'].flatMap((label) => ['ana', 'bruno'].map((alunoId, i) => ({
    label, sessaoId: 'sessao', alunoId, success: 'true', responseCode: '200', elapsed: String(30 + i), timeStamp: String(5000 + i),
  })));
  const estados = [{ sessaoId: 'sessao', modo: 'QRCODE', totalMatriculados: 2,
    presentes: ['ana', 'bruno'].map((alunoId) => ({ alunoId, origem: 'QRCODE' })) }];
  return { manifesto, amostras, estados };
}

test('cenário pequeno válido não é apresentado como carga completa', () => {
  const { manifesto, amostras, estados } = cenario();
  const resultado = resumir(manifesto, amostras, estados);
  assert.equal(resultado.criteriosAtendidos, true);
  assert.equal(resultado.cargaCompleta, false);
  assert.equal(resultado.checkin.latenciaMs.maximo, 31);
});

test('detecta check-in confirmado que não foi persistido', () => {
  const { manifesto, amostras, estados } = cenario();
  estados[0].presentes.pop();
  const resultado = resumir(manifesto, amostras, estados);
  assert.equal(resultado.criteriosAtendidos, false);
  assert.deepEqual(resultado.persistencia.perdidos, ['sessao/bruno']);
});

test('latência de 1000 ms reprova mesmo com registros persistidos', () => {
  const { manifesto, amostras, estados } = cenario();
  amostras[2].elapsed = '1000';
  assert.equal(resumir(manifesto, amostras, estados).criteriosAtendidos, false);
});

test('reprova cobertura duplicada, falhas de auth e resposta de protocolo recusada', () => {
  for (const alterar of [
    (a) => { a[3].alunoId = 'ana'; },
    (a) => { a[0].success = 'false'; },
    (a) => { a[2].responseCode = 'FALHA'; },
    (a) => { a.pop(); },
  ]) {
    const { manifesto, amostras, estados } = cenario(); alterar(amostras);
    assert.equal(resumir(manifesto, amostras, estados).criteriosAtendidos, false);
  }
});

test('não aceita barreira que libera check-ins em instantes muito separados', () => {
  const { manifesto, amostras, estados } = cenario();
  amostras[3].timeStamp = '7000';
  assert.equal(resumir(manifesto, amostras, estados).criteriosAtendidos, false);
});

test('confere identidade, duplicação e origem dos registros persistidos', () => {
  for (const alterar of [
    (e) => { e[0].presentes[1].alunoId = 'outra-pessoa'; },
    (e) => { e[0].presentes.push({ alunoId: 'ana', origem: 'QRCODE' }); },
    (e) => { e[0].presentes[0].origem = 'MANUAL'; },
  ]) {
    const { manifesto, amostras, estados } = cenario(); alterar(estados);
    assert.equal(resumir(manifesto, amostras, estados).criteriosAtendidos, false);
  }
});

test('CSV preserva vírgulas, aspas e mensagens com quebras de linha', () => {
  const linhas = [{ label: 'checkin', mensagem: 'erro, "detalhe"\nmais informação' }];
  assert.deepEqual(lerCsv(escreverCsv(linhas)), linhas);
  assert.throws(() => lerCsv('a,b\n"aberto'), /aspas/);
});
