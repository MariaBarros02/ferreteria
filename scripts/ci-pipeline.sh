#!/usr/bin/env bash
######################################################################
# Nombre: ci-pipeline.sh
# Descripción: Pipeline CI/CD local: detecta cambios en el repositorio, compila y prueba; sin errores despliega; con errores crea issue y tareas en Taiga y notifica.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
# Uso:
#   ./scripts/ci-pipeline.sh --once              Revisa una vez (apto para cron)
#   ./scripts/ci-pipeline.sh --watch [segundos]  Revisa en bucle (por defecto cada 60 s)
#   Opciones: --sin-despliegue  --forzar (ejecuta aunque no haya cambios)
set -uo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"; cd "$RAIZ"
[ -f .env ] && set -a && . ./.env && set +a
RAMA="${RAMA_PRINCIPAL:-main}"
ESTADO="${CI_STATE_DIR:-$RAIZ/.ci-state}"; LOGS="$ESTADO/logs"; mkdir -p "$LOGS"
MODO=once; INTERVALO=60; DESPLEGAR=1; FORZAR=0
while [ $# -gt 0 ]; do case "$1" in
  --once) MODO=once;; --watch) MODO=watch; [[ "${2:-}" =~ ^[0-9]+$ ]] && { INTERVALO="$2"; shift; };;
  --sin-despliegue) DESPLEGAR=0;; --forzar) FORZAR=1;; esac; shift; done

registrar_corrida() { # resultado etapa_fallida sha duracion
  printf '{"fecha":"%s","sha":"%s","resultado":"%s","etapa":"%s","segundos":%s}\n' \
    "$(date -u +%FT%TZ)" "$3" "$1" "$2" "$4" >> "$ESTADO/runs.jsonl"; }

ejecutar_etapa() { # nombre comando...
  local nombre="$1"; shift
  echo ">> etapa: $nombre"
  if "$@" > "$LOGS/$nombre.log" 2>&1; then echo "   OK"; return 0; fi
  echo "   FALLÓ (ver $LOGS/$nombre.log)"; return 1
}

reportar() { # etapa sha
  local etapa="$1" sha="$2"
  python3 "$RAIZ/scripts/taiga/taiga_client.py" reportar-fallo --etapa "$etapa" --sha "$sha" --rama "$RAMA" \
    --autor "$(git log -1 --format=%an "$sha")" --correo "$(git log -1 --format=%ae "$sha")" --log "$LOGS/$etapa.log" \
    || echo "!! No se pudo reportar a Taiga (revise TAIGA_* y conectividad). El fallo queda en $LOGS/$etapa.log"
}

una_pasada() {
  git fetch origin "$RAMA" -q || { echo "!! git fetch falló"; return 0; }
  local remoto previo; remoto="$(git rev-parse "origin/$RAMA")"; previo="$(cat "$ESTADO/last_sha" 2>/dev/null || echo "")"
  if [ "$remoto" = "$previo" ] && [ "$FORZAR" = 0 ]; then echo "Sin cambios ($remoto)"; return 0; fi
  echo "== Cambios detectados: ${previo:-<primera vez>} -> $remoto"
  git checkout -q "$RAMA" && git merge --ff-only "origin/$RAMA" -q || { echo "!! No se pudo avanzar $RAMA (¿cambios locales?)"; return 0; }
  local cambios; cambios="$( [ -n "$previo" ] && git diff --name-only "$previo" "$remoto" || git ls-files )"
  local be=0 fe=0
  echo "$cambios" | grep -Eq '^(backend/|database/)' && be=1
  echo "$cambios" | grep -Eq '^frontend/' && fe=1
  echo "$cambios" | grep -Eq '^(docker-compose|scripts/)' && { be=1; fe=1; }
  [ "$FORZAR" = 1 ] && { be=1; fe=1; }
  echo "   backend/database: $be  frontend: $fe"
  local t0=$SECONDS fallo=""
  [ $be = 1 ] && { ejecutar_etapa backend ./scripts/build-test-backend.sh || fallo="backend"; }
  [ $fe = 1 ] && { ejecutar_etapa frontend ./scripts/build-test-frontend.sh || fallo="${fallo:+$fallo,}frontend"; }
  echo "$remoto" > "$ESTADO/last_sha"   # evita reportar en bucle el mismo commit
  if [ -n "$fallo" ]; then
    for e in ${fallo//,/ }; do reportar "$e" "$remoto"; done
    registrar_corrida fallo "$fallo" "$remoto" $((SECONDS - t0)); return 0
  fi
  if [ "$DESPLEGAR" = 1 ]; then
    if ejecutar_etapa despliegue ./scripts/deploy.sh; then
      registrar_corrida ok "" "$remoto" $((SECONDS - t0)); git tag -f "deploy-$(date +%Y%m%d-%H%M%S)" "$remoto" >/dev/null
    else reportar despliegue "$remoto"; registrar_corrida fallo despliegue "$remoto" $((SECONDS - t0)); fi
  else registrar_corrida ok "" "$remoto" $((SECONDS - t0)); echo "Pruebas OK (despliegue omitido)"; fi
}

exec 9>"$ESTADO/pipeline.lock"; flock -n 9 || { echo "Ya hay un pipeline en ejecución"; exit 0; }
if [ "$MODO" = once ]; then una_pasada; else while true; do una_pasada; sleep "$INTERVALO"; done; fi
