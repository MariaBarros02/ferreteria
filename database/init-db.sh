#!/usr/bin/env bash
######################################################################
# Nombre: init-db.sh
# Descripción: Crea e inicializa la base de datos PostgreSQL (DDL, seed, pruebas y verificaciones) con psql.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
# Uso: ./init-db.sh [--sin-seed] [--sin-pruebas]
# Variables: PGHOST PGPORT PGUSER PGPASSWORD DB_NAME (por defecto ferreteria_db)
set -euo pipefail
DB_NAME="${DB_NAME:-ferreteria_db}"
AQUI="$(cd "$(dirname "$0")" && pwd)"
SEED=1; PRUEBAS=1
for a in "$@"; do case "$a" in --sin-seed) SEED=0;; --sin-pruebas) PRUEBAS=0;; esac; done

echo ">> Creando base de datos ${DB_NAME} (si no existe)"
psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 \
  || createdb "${DB_NAME}"

echo ">> 01_ddl.sql"
psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/01_ddl.sql"
if [ "$SEED" = 1 ]; then echo ">> 02_seed.sql"; psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/02_seed.sql"; fi
if [ "$PRUEBAS" = 1 ]; then
  echo ">> 03_pruebas.sql"; psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/03_pruebas.sql"
  echo ">> Resultado de pruebas de reglas de negocio"
  psql -d "${DB_NAME}" -c "SELECT regla, resultado, COUNT(*) FROM test_resultados GROUP BY 1,2 ORDER BY 1;"
  FALLOS=$(psql -d "${DB_NAME}" -tAc "SELECT COUNT(*) FROM test_resultados WHERE resultado='FALLO'")
  [ "$FALLOS" = "0" ] || { echo "!! ${FALLOS} pruebas de BD fallaron"; exit 1; }
fi
echo ">> 04_verificaciones.sql (todas las consultas deben devolver 0 filas)"
psql -d "${DB_NAME}" -f "${AQUI}/04_verificaciones.sql"
echo ">> Base ${DB_NAME} lista."
