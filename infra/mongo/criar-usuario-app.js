// Cria o usuário da aplicação, usado pela API e pelo servidor de sessão. A imagem do MongoDB roda
// este arquivo uma única vez, no primeiro start do mongo1 com o volume vazio, já autenticada como
// root (MONGO_INITDB_ROOT_*).
//
// Permissão mínima: readWrite só no banco fast_attendance. Cobre o CRUD, a criação dos índices na
// subida da API e os Change Streams do painel ao vivo. Não administra o servidor nem outros bancos.
const usuario = process.env.MONGO_APP_USUARIO || "fast_attendance";
const senha = process.env.MONGO_APP_SENHA;
if (!senha) throw new Error("MONGO_APP_SENHA não definida");

const banco = db.getSiblingDB("fast_attendance");
if (banco.getUser(usuario)) {
  print(`Usuário ${usuario} já existe.`);
} else {
  banco.createUser({ user: usuario, pwd: senha, roles: [{ role: "readWrite", db: "fast_attendance" }] });
  print(`Usuário ${usuario} criado com readWrite em fast_attendance.`);
}
