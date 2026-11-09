#!/bin/sh
# Prova que o backup mais recente restaura: carrega o dump num banco temporário, confere e apaga.
# Uso (na pasta do docker-compose.yml, com o .env): scripts/restore-test.sh [opções do docker compose, ex.: -p nome]
set -eu
export MSYS_NO_PATHCONV=1   # Git Bash no Windows reescreveria /backups/...
DC="docker compose $*"
USER_DB=${DB_USER:-biblioteca}

latest=$($DC --profile full exec -T backup sh -c 'ls -t /backups/biblioteca-*.dump | head -1')
[ -n "$latest" ] || { echo "FALHOU: nenhum backup em /backups" >&2; exit 1; }
echo "Backup: $latest"

$DC exec -T postgres psql -U "$USER_DB" -d postgres -c "DROP DATABASE IF EXISTS restore_test" -c "CREATE DATABASE restore_test" >/dev/null
trap '$DC exec -T postgres psql -U "$USER_DB" -d postgres -c "DROP DATABASE IF EXISTS restore_test" >/dev/null' EXIT

$DC --profile full exec -T backup cat "$latest" | $DC exec -T postgres pg_restore -U "$USER_DB" -d restore_test --no-owner

tables=$($DC exec -T postgres psql -U "$USER_DB" -d restore_test -tAc "select count(*) from information_schema.tables where table_schema='public'")
migrations=$($DC exec -T postgres psql -U "$USER_DB" -d restore_test -tAc "select count(*) from flyway_schema_history where success")
users=$($DC exec -T postgres psql -U "$USER_DB" -d restore_test -tAc "select count(*) from users")
echo "Restaurado: $tables tabelas, $migrations migrações, $users usuários"
[ "$tables" -gt 0 ] && [ "$migrations" -gt 0 ] || { echo "FALHOU: restauração incompleta" >&2; exit 1; }
echo "OK: o backup restaura."
