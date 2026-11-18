export function resumir(manifesto, amostras, persistencias) {
  const chave = (sessaoId, alunoId) => `${sessaoId}/${alunoId}`;
  const esperados = new Set(manifesto.sessoes.flatMap((s) => s.alunos.map((a) => chave(s.sessaoId, a))));
  if (!manifesto.pronto || esperados.size !== manifesto.total || manifesto.sessoes.length !== manifesto.salas
      || manifesto.total !== manifesto.salas * manifesto.alunosPorSala
      || manifesto.sessoes.some((s) => s.alunos.length !== manifesto.alunosPorSala)) {
    throw new Error('Manifesto incompleto ou inconsistente.');
  }
  const problemas = [];
  const grupos = {};
  for (const rotulo of ['auth', 'checkin']) {
    const linhas = amostras.filter((a) => a.label === rotulo);
    const identidades = linhas.map((a) => chave(a.sessaoId, a.alunoId));
    if (linhas.length !== esperados.size || new Set(identidades).size !== esperados.size
        || identidades.some((id) => !esperados.has(id))) problemas.push(`${rotulo}: cobertura diferente dos alunos esperados.`);
    if (linhas.some((a) => !['true', 'false'].includes(a.success) || !Number.isFinite(Number(a.elapsed))
        || Number(a.elapsed) < 0 || !Number.isFinite(Number(a.timeStamp)))) throw new Error('JTL com amostra inválida.');
    const erros = linhas.filter((a) => a.success !== 'true' || a.responseCode !== '200');
    if (erros.length) problemas.push(`${rotulo}: ${erros.length} erros.`);
    grupos[rotulo] = { linhas, erros };
  }
  const checkins = grupos.checkin.linhas;
  const tempos = checkins.map((a) => Number(a.elapsed)).sort((a, b) => a - b);
  const percentil = (p) => tempos.length ? tempos[Math.ceil(p * tempos.length) - 1] : null;
  const maximo = tempos.at(-1) ?? null;
  if (maximo === null || maximo >= 1000) problemas.push('Check-in deve levar menos de 1000 ms em todas as amostras.');
  const inicios = checkins.map((a) => Number(a.timeStamp));
  const dispersao = inicios.length ? Math.max(...inicios) - Math.min(...inicios) : null;
  if (dispersao === null || dispersao > 1000) problemas.push('Dispersão do início dos check-ins maior que 1000 ms.');
  const gravados = new Set();
  let duplicadosNoBanco = 0;
  for (const s of manifesto.sessoes) {
    const estado = persistencias.find((p) => p.sessaoId === s.sessaoId);
    if (!estado || estado.totalMatriculados !== manifesto.alunosPorSala || estado.modo !== manifesto.canal) {
      throw new Error('Estado persistido ausente ou incompatível com a execução.');
    }
    for (const presenca of estado.presentes) {
      const id = chave(s.sessaoId, presenca.alunoId);
      if (gravados.has(id)) duplicadosNoBanco++;
      gravados.add(id);
      if (presenca.origem !== manifesto.canal) problemas.push(`Origem de presença incorreta: ${id}`);
    }
  }
  const confirmados = new Set(checkins.filter((a) => a.success === 'true' && a.responseCode === '200')
    .map((a) => chave(a.sessaoId, a.alunoId)));
  const ausentes = [...esperados].filter((id) => !gravados.has(id));
  const perdidos = [...confirmados].filter((id) => !gravados.has(id));
  const extras = [...gravados].filter((id) => !esperados.has(id));
  if (ausentes.length || perdidos.length || extras.length || duplicadosNoBanco) problemas.push('Conferência de persistência falhou.');
  return { execucao: manifesto.id, canal: manifesto.canal, salas: manifesto.salas,
    alunosPorSala: manifesto.alunosPorSala, esperados: esperados.size,
    cargaCompleta: manifesto.salas === 15 && manifesto.alunosPorSala === 70,
    criteriosAtendidos: problemas.length === 0, problemas,
    auth: { amostras: grupos.auth.linhas.length, erros: grupos.auth.erros.length },
    checkin: { amostras: checkins.length, erros: grupos.checkin.erros.length,
      taxaErros: checkins.length ? grupos.checkin.erros.length / checkins.length : null,
      latenciaMs: { p50: percentil(0.5), p95: percentil(0.95), p99: percentil(0.99), maximo }, dispersaoInicioMs: dispersao },
    persistencia: { confirmados: confirmados.size, gravados: gravados.size, ausentes, perdidos, extras, duplicadosNoBanco } };
}
