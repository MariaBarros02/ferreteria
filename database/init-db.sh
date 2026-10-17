#!/usr/bin/env bash
######################################################################
# Nombre: init-db.sh
# Descripción: Crea e inicializa la base de datos PostgreSQL (DDL, migraciones, seed, pruebas y verificaciones) con psql.
#              Orden: 01_ddl (=V1) → V2 → seed → V3..Vn. Así las migraciones nuevas se ejercitan sobre datos
#              existentes (compatibilidad). Las migraciones V2+ son idempotentes: Flyway puede reaplicarlas tras el baseline.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-16
# Autor: Equipo Ferretería
# Versión: 1.1.0
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

echo ">> 01_ddl.sql (equivale a V1)"
psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/01_ddl.sql"
echo ">> migrations/V2__roles_permisos.sql"
psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/migrations/V2__roles_permisos.sql"
if [ "$SEED" = 1 ]; then echo ">> 02_seed.sql"; psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/02_seed.sql"; fi
# Migraciones posteriores a V2, en orden de versión numérica
for f in $(ls "${AQUI}"/migrations/V*__*.sql | sed -E 's|.*/V([0-9]+)__.*|\1 &|' | sort -n | awk '$1 > 2 {print $2}'); do
  echo ">> migrations/$(basename "$f")"
  psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "$f"
done
if [ "$PRUEBAS" = 1 ]; then
  echo ">> 03_pruebas.sql"; psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/03_pruebas.sql"
  echo ">> Resultado de pruebas de reglas de negocio"
  psql -d "${DB_NAME}" -c "SELECT regla, resultado, COUNT(*) FROM test_resultados GROUP BY 1,2 ORDER BY 1;"
  FALLOS=$(psql -d "${DB_NAME}" -tAc "SELECT COUNT(*) FROM test_resultados WHERE resultado='FALLO'")
  [ "$FALLOS" = "0" ] || { echo "!! ${FALLOS} pruebas de BD fallaron"; exit 1; }
fi
echo ">> 04_verificaciones.sql (todas las consultas deben devolver 0 filas)"
SALIDA=$(psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${AQUI}/04_verificaciones.sql")
echo "$SALIDA"
if echo "$SALIDA" | grep -qE '^\(([1-9][0-9]*) rows?\)$'; then echo "!! Alguna verificación de integridad devolvió filas"; exit 1; fi
echo ">> Base ${DB_NAME} lista."
