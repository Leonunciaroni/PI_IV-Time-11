// Dados de demonstração para o dashboard e a apresentação: turmas, salas, matrículas, aulas
// encerradas e presenças nos três modos. Chamado por infra/dados-demo.sh, depois que as contas já
// foram criadas pela API (a senha precisa do hash BCrypt da API).
//
// Idempotente: turmas são encontradas pelo nome e pela disciplina, salas pelo nome, matrículas pelo par turma + aluno, e as
// aulas só são criadas na turma que ainda não tem nenhuma aula encerrada.
//
// As aulas e presenças vão direto no banco porque a API não abre nem encerra aulas: isso é feito
// pelo servidor de sessão, ao vivo. Os campos seguem docs/modelo-de-dados.md.

const PROFESSOR = "professor@demo.br";
const MINIMO = 75;

const SALAS = [
  // Coordenadas aproximadas do campus I da PUC-Campinas
  { nome: "H15 - Sala 203", latitude: -22.83402, longitude: -47.05298, raioMetros: 60 },
  { nome: "H15 - Laboratório 4", latitude: -22.83431, longitude: -47.05262, raioMetros: 40 },
];

// diaDaSemana: 0 = domingo; hora em Brasília (UTC-3, sem horário de verão)
const TURMAS = [
  {
    nome: "Turma A",
    disciplina: "Engenharia de Software I",
    horarioPadrao: "Seg 19:00",
    diaDaSemana: 1,
    hora: 19,
    sala: "H15 - Sala 203",
    alunos: ["ana@demo.br", "bruno@demo.br", ...faixa(1, 30)],
    // 12 aulas, como no wireframe P9: Clássica 2 · QR Code 7 · Som 3
    modos: ["CLASSICA", "QRCODE", "QRCODE", "SOM", "QRCODE", "CLASSICA",
            "QRCODE", "SOM", "QRCODE", "QRCODE", "SOM", "QRCODE"],
  },
  {
    nome: "Turma B",
    disciplina: "Estrutura de Dados",
    horarioPadrao: "Qua 21:00",
    diaDaSemana: 3,
    hora: 21,
    sala: "H15 - Laboratório 4",
    alunos: ["ana@demo.br", ...faixa(31, 60)],
    modos: ["CLASSICA", "QRCODE", "SOM", "QRCODE", "CLASSICA",
            "QRCODE", "QRCODE", "SOM", "CLASSICA", "QRCODE"],
  },
];

// Alunos que faltam mais, para o dashboard ter quem fique abaixo de 75%
const FALTOSOS = {
  "bruno@demo.br": 0.62,
  "aluno07@demo.br": 0.55,
  "aluno15@demo.br": 0.68,
  "aluno22@demo.br": 0.45,
  "aluno38@demo.br": 0.6,
  "aluno44@demo.br": 0.7,
  "aluno53@demo.br": 0.5,
};

function faixa(inicio, fim) {
  const emails = [];
  for (let i = inicio; i <= fim; i++) emails.push(`aluno${String(i).padStart(2, "0")}@demo.br`);
  return emails;
}

// Sorteio com semente fixa: rodar de novo num banco limpo gera os mesmos dados
function sorteador(semente) {
  let estado = semente >>> 0;
  return () => {
    estado = (estado + 0x6d2b79f5) >>> 0;
    let t = estado;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hash(texto) {
  let h = 2166136261;
  for (const c of texto) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return h >>> 0;
}

/** UUID estável por aluno, no lugar do id que o navegador do aluno gera. */
function dispositivoDe(email) {
  const sortear = sorteador(hash("dispositivo:" + email));
  const hex = Array.from({ length: 32 }, () => Math.floor(sortear() * 16).toString(16)).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
}

function segredoTotp(sortear) {
  const base32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  return Array.from({ length: 32 }, () => base32[Math.floor(sortear() * 32)]).join("");
}

/** Datas das últimas `quantidade` aulas no dia e hora da turma, todas antes de agora. */
function datasDasAulas(turma, quantidade) {
  const agora = new Date();
  const data = new Date(Date.UTC(agora.getUTCFullYear(), agora.getUTCMonth(), agora.getUTCDate(), turma.hora + 3));
  // Volta até o dia da semana da turma, no horário de Brasília
  while (new Date(data.getTime() - 3 * 3600e3).getUTCDay() !== turma.diaDaSemana || data >= agora) {
    data.setUTCDate(data.getUTCDate() - 1);
  }
  const datas = [];
  for (let i = quantidade - 1; i >= 0; i--) datas.push(new Date(data.getTime() - i * 7 * 86400e3));
  return datas;
}

function minutos(data, quantos) {
  return new Date(data.getTime() + quantos * 60e3);
}

// ---- Professor e alunos (criados antes pela API) ----

const professor = db.usuarios.findOne({ email: PROFESSOR, papel: "PROFESSOR" });
if (!professor) {
  print(`Erro: ${PROFESSOR} não existe. Rode infra/dados-demo.sh, que cria as contas pela API.`);
  quit(1);
}
const professorId = professor._id.toString();

const emails = [...new Set(TURMAS.flatMap((t) => t.alunos))];
const alunos = new Map(
  db.usuarios.find({ email: { $in: emails }, papel: "ALUNO" }).toArray().map((u) => [u.email, u]),
);
const faltando = emails.filter((e) => !alunos.has(e));
if (faltando.length > 0) {
  print(`Erro: ${faltando.length} aluno(s) sem conta, por exemplo ${faltando[0]}.`);
  quit(1);
}

// ---- Salas ----

const salas = new Map();
for (const sala of SALAS) {
  const resultado = db.salas.findOneAndUpdate(
    { nome: sala.nome },
    { $setOnInsert: sala },
    { upsert: true, returnDocument: "after" },
  );
  salas.set(sala.nome, resultado._id.toString());
}
print(`salas      ${SALAS.length}`);

// ---- Turmas, matrículas, aulas e presenças ----

for (const turma of TURMAS) {
  const documento = db.turmas.findOneAndUpdate(
    { professorId, nome: turma.nome, disciplina: turma.disciplina },
    {
      $setOnInsert: {
        nome: turma.nome,
        disciplina: turma.disciplina,
        horarioPadrao: turma.horarioPadrao,
        professorId,
        criadaEm: new Date(),
      },
    },
    { upsert: true, returnDocument: "after" },
  );
  const turmaId = documento._id.toString();

  let novas = 0;
  for (const email of turma.alunos) {
    const r = db.matriculas.updateOne(
      { turmaId, alunoId: alunos.get(email)._id.toString() },
      { $setOnInsert: { criadaEm: new Date() } },
      { upsert: true },
    );
    novas += r.upsertedCount;
  }

  const rotulo = `${turma.disciplina} · ${turma.nome}`;
  if (db.sessoes.countDocuments({ turmaId, status: "ENCERRADA" }) > 0) {
    print(`turma      ${rotulo}: ${turma.alunos.length} alunos (${novas} novos); já tem aulas, mantidas`);
    continue;
  }

  const sortear = sorteador(hash(turma.nome));
  const sala = SALAS.find((s) => s.nome === turma.sala);
  const datas = datasDasAulas(turma, turma.modos.length);
  let presencas = 0;

  turma.modos.forEach((modo, i) => {
    const abertaEm = datas[i];
    const sessao = {
      _id: new ObjectId(),
      turmaId,
      professorId,
      modo,
      salaId: modo === "QRCODE" ? salas.get(turma.sala) : null,
      segredoTotp: segredoTotp(sortear),
      status: "ENCERRADA",
      abertaEm,
      encerradaEm: minutos(abertaEm, 100),
      motivoEncerramento: "PROFESSOR",
    };
    db.sessoes.insertOne(sessao);
    const sessaoId = sessao._id.toString();

    const registros = [];
    for (const email of turma.alunos) {
      const chance = FALTOSOS[email] ?? 0.93;
      if (sortear() >= chance) continue; // faltou: sem registro conta como ausente
      const presenca = {
        sessaoId,
        turmaId,
        alunoId: alunos.get(email)._id.toString(),
        presente: true,
        origem: modo === "CLASSICA" ? "MANUAL" : modo,
        dispositivoId: modo === "CLASSICA" ? null : dispositivoDe(email),
        latitude: null,
        longitude: null,
        registradoEm: minutos(abertaEm, 2 + Math.floor(sortear() * 15)),
      };
      if (modo === "QRCODE") {
        // Dentro do raio: até ~20 m do centro da sala
        presenca.latitude = sala.latitude + (sortear() - 0.5) * 0.0003;
        presenca.longitude = sala.longitude + (sortear() - 0.5) * 0.0003;
      }
      // Na clássica, de vez em quando o professor marca e depois desmarca (presente: false)
      if (modo === "CLASSICA" && sortear() < 0.03) presenca.presente = false;
      registros.push(presenca);
    }
    if (registros.length > 0) db.presencas.insertMany(registros);
    presencas += registros.length;
  });

  print(`turma      ${rotulo}: ${turma.alunos.length} alunos, ${turma.modos.length} aulas, ${presencas} presenças`);
}

print(`(abaixo de ${MINIMO}% de propósito: ${Object.keys(FALTOSOS).join(", ")})`);
