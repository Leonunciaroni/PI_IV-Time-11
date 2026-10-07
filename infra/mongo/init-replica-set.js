// Inicializa o replica set rs0 com três membros. Idempotente: se o replica set já existir,
// apenas aguarda a eleição do primário.
const config = {
  _id: "rs0",
  members: [
    { _id: 0, host: "mongo1:27017", priority: 2 },
    { _id: 1, host: "mongo2:27017", priority: 1 },
    { _id: 2, host: "mongo3:27017", priority: 1 },
  ],
};

try {
  rs.status();
  print("Replica set rs0 já inicializado.");
} catch (e) {
  if (e.codeName !== "NotYetInitialized") throw e;
  rs.initiate(config);
  print("Replica set rs0 inicializado.");
}

for (let i = 0; i < 60; i++) {
  if (db.hello().isWritablePrimary) {
    print("Primário eleito: " + db.hello().me);
    quit(0);
  }
  sleep(1000);
}
print("Timeout aguardando a eleição do primário.");
quit(1);
