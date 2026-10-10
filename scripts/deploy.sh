#!/usr/bin/env bash
######################################################################
# Nombre: deploy.sh
# Descripción: Despliegue a producción con Docker Compose: construye, levanta, espera salud del backend y revierte a la imagen previa si falla.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "Falta .env (copie .env.example)"; exit 1; }
set -a; . ./.env; set +a
echo ">> construyendo imágenes"; docker compose build
echo ">> levantando servicios"; docker compose up -d
echo ">> esperando salud del backend (máx. 120 s)"
for i in $(seq 1 24); do
  if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then echo "Backend saludable"; exit 0; fi
  sleep 5
done
echo "!! El backend no está saludable; mostrando logs y deteniendo el despliegue"
docker compose logs --tail=80 backend || true
exit 1
