#!/usr/bin/env bash
# Autores:
# - João Pedro Panza Mainieri (RA 25006642)
# Cria contas de demonstração pela API (POST /api/auth/registro).
# Idempotente: contas já existentes (HTTP 409) são mantidas.
#   ./infra/usuarios-demo.sh [URL_BASE]     (padrão: http://localhost:8088)
#
# Senha de todas as contas: demo12345 — apenas para o ambiente local.
set -euo pipefail

BASE="${1:-http://localhost:8088}"
SENHA="demo12345"

registrar() {
  local nome="$1" email="$2" papel="$3" matricula="$4"
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"nome\":\"$nome\",\"email\":\"$email\",\"senha\":\"$SENHA\",\"papel\":\"$papel\",\"matricula\":\"$matricula\"}" \
    "$BASE/api/auth/registro")
  case "$status" in
    201) echo "criada    $papel  $email" ;;
    409) echo "já existe $papel  $email" ;;
    403) echo "erro: $email não está em PROFESSORES_PERMITIDOS (infra/.env)" >&2; exit 1 ;;
    *)   echo "erro $status ao criar $email" >&2; exit 1 ;;
  esac
}

registrar "Matheus Oliveira" "professor@demo.br" PROFESSOR "P0001"
registrar "Ana Souza"        "ana@demo.br"       ALUNO     "24001234"
registrar "Bruno Lima"       "bruno@demo.br"     ALUNO     "24001777"

echo
echo "Senha de todas as contas: $SENHA"
echo "Professor: $BASE/professor   Aluno: $BASE/aluno"
