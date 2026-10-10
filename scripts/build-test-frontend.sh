#!/usr/bin/env bash
######################################################################
# Nombre: build-test-frontend.sh
# Descripción: Compila y prueba el frontend: instalación limpia, pruebas unitarias con cobertura, build y JSDoc. Opcionales: e2e y mutación.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
set -euo pipefail
cd "$(dirname "$0")/../frontend"
E2E=0; MUT=0
for a in "$@"; do case "$a" in --e2e) E2E=1;; --mutacion) MUT=1;; esac; done
echo ">> [frontend] instalación reproducible"; npm ci --no-audit --no-fund
echo ">> [frontend] pruebas unitarias y de componente (con cobertura)"; npm run test:coverage
echo ">> [frontend] build de producción"; npm run build
echo ">> [frontend] JSDoc"; npm run docs
echo ">> [frontend] auditoría de dependencias (solo alto/crítico)"; npm audit --audit-level=high || { echo "!! vulnerabilidades altas"; exit 1; }
if [ "$E2E" = 1 ]; then
  npx playwright install --with-deps chromium
  echo ">> [frontend] E2E Playwright"; npm run test:e2e
  echo ">> [frontend] Aceptación Selenium"; npm run test:selenium
fi
if [ "$MUT" = 1 ]; then echo ">> [frontend] mutación (Stryker)"; npm run test:mutation; fi
echo ">> [frontend] OK"
