#!/usr/bin/env bash
# Dados de demonstração para o dashboard e a apresentação (#71):
#   - as contas de usuarios-demo.sh e mais 60 alunos (aluno01@demo.br ... aluno60@demo.br), pela API;
#   - 2 turmas do professor@demo.br com 30+ alunos cada, 2 salas, aulas encerradas e presenças nos
#     três modos, direto no MongoDB (mongo/dados-demo.js).
# Idempotente: pode rodar de novo sem duplicar nada.
#   ./infra/dados-demo.sh [URL_BASE]     (padrão: http://localhost:8088, com o Compose rodando)
#
# Senha de todas as contas: demo12345 — apenas para o ambiente local.
set -euo pipefail

cd "$(dirname "$0")"
BASE="${1:-http://localhost:8088}"
SENHA="demo12345"
MONGO_URI="${MONGO_URI:-mongodb://mongo1:27017,mongo2:27017,mongo3:27017/fast_attendance?replicaSet=rs0}"

./usuarios-demo.sh "$BASE"
echo

NOMES=(
  "Alice Ferreira" "Arthur Almeida" "Beatriz Costa" "Bernardo Ribeiro" "Camila Martins"
  "Caio Barbosa" "Carolina Rocha" "Daniel Carvalho" "Débora Gomes" "Diego Pereira"
  "Eduarda Lopes" "Enzo Cardoso" "Fernanda Teixeira" "Felipe Moreira" "Gabriela Nunes"
  "Gustavo Araújo" "Helena Mendes" "Heitor Castro" "Isabela Pinto" "Igor Freitas"
  "Júlia Correia" "João Pedro Dias" "Larissa Monteiro" "Lucas Cavalcanti" "Manuela Ramos"
  "Matheus Fernandes" "Mariana Batista" "Miguel Duarte" "Natália Vieira" "Nicolas Farias"
  "Olívia Machado" "Otávio Rezende" "Paula Campos" "Pedro Henrique Lima" "Rafaela Azevedo"
  "Rafael Moura" "Sofia Cunha" "Samuel Peixoto" "Tainá Brandão" "Thiago Siqueira"
  "Valentina Prado" "Vinícius Antunes" "Yasmin Tavares" "Murilo Fontes" "Lívia Sampaio"
  "Leonardo Bastos" "Lorena Guimarães" "Henrique Pacheco" "Giovana Sales" "Fábio Matos"
  "Elisa Coelho" "Davi Moraes" "Clara Andrade" "Benício Rangel" "Alana Vasconcelos"
  "Augusto Leal" "Bianca Quintela" "Cauã Neves" "Melissa Torres" "Rodrigo Aguiar"
)

criadas=0
existentes=0
for i in "${!NOMES[@]}"; do
  n=$(printf '%02d' $((i + 1)))
  # Corpo pela entrada padrão: no Git Bash do Windows, um argumento com acento chega ao curl fora
  # do UTF-8 e a API responde 400
  status=$(printf '{"nome":"%s","email":"aluno%s@demo.br","senha":"%s","papel":"ALUNO","matricula":"241000%s"}' \
      "${NOMES[$i]}" "$n" "$SENHA" "$n" |
    curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' --data-binary @- \
      "$BASE/api/auth/registro")
  case "$status" in
    201) criadas=$((criadas + 1)) ;;
    409) existentes=$((existentes + 1)) ;;
    *)   echo "erro $status ao criar aluno$n@demo.br" >&2; exit 1 ;;
  esac
done
echo "alunos     $criadas criados, $existentes já existiam (aluno01@demo.br ... aluno60@demo.br)"

# Turmas, salas, aulas e presenças direto no banco, pelo mongosh do próprio container
docker compose cp mongo/dados-demo.js mongo1:/tmp/dados-demo.js >/dev/null 2>&1 ||
  { echo "erro: não foi possível copiar o script para o mongo1 (o Compose está rodando?)" >&2; exit 1; }
# Caminho dentro do sh -c: o Git Bash do Windows converteria um argumento /tmp/... para C:/...
docker compose exec -T mongo1 sh -c 'mongosh "$1" --quiet /tmp/dados-demo.js' sh "$MONGO_URI"

echo
echo "Senha de todas as contas: $SENHA"
echo "Professor: $BASE/professor (professor@demo.br)   Aluno: $BASE/aluno"
