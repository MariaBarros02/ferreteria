#!/usr/bin/env bash
######################################################################
# Nombre: build-test-backend.sh
# Descripción: Compila y prueba el backend: unitarias, integración/sistema/seguridad (Testcontainers), cobertura y Javadoc. Opcional: mutación.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
set -euo pipefail
cd "$(dirname "$0")/../backend"
MUTACION=0; for a in "$@"; do [ "$a" = "--mutacion" ] && MUTACION=1; done
echo ">> [backend] compilar y ejecutar pruebas unitarias + integración (requiere Docker para *IT)"
mvn -B -ntp clean verify
echo ">> [backend] Javadoc"
mvn -B -ntp javadoc:javadoc
if [ "$MUTACION" = 1 ]; then
  echo ">> [backend] pruebas de mutación (PIT)"
  mvn -B -ntp org.pitest:pitest-maven:mutationCoverage
fi
echo ">> [backend] OK. Reportes: target/site/jacoco, target/surefire-reports, target/failsafe-reports, target/pit-reports"
