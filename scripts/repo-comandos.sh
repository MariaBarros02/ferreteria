#!/usr/bin/env bash
######################################################################
# Nombre: repo-comandos.sh
# Descripción: Comandos de gestión del repositorio Git: inicializar, ramas por tarea, sincronizar, etiquetar versión y detectar cambios.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
# Uso: ./scripts/repo-comandos.sh <comando> [args]
set -euo pipefail
cd "$(dirname "$0")/.."
RAMA_PRINCIPAL="${RAMA_PRINCIPAL:-main}"

uso() { cat <<TXT
Comandos:
  init <url-remoto>        Inicializa el repo, activa los hooks de .githooks y publica $RAMA_PRINCIPAL
  tarea <id-taiga> <desc>  Crea la rama feature/TG-<id>-<desc> desde $RAMA_PRINCIPAL actualizada
  commit "<mensaje>"       git add -A + commit (el hook pre-commit ejecuta pruebas rápidas)
  sync                     Trae cambios del remoto con rebase
  publicar                 Empuja la rama actual (el hook pre-push ejecuta las pruebas)
  version <x.y.z>          Crea y publica la etiqueta v<x.y.z>
  cambios                  Lista los archivos modificados frente al remoto (qué parte cambió)
  estado                   Resumen del repositorio
TXT
}

cmd="${1:-}"; shift || true
case "$cmd" in
  init)    [ $# -eq 1 ] || { uso; exit 1; }
           [ -d .git ] || git init -b "$RAMA_PRINCIPAL"
           git config core.hooksPath .githooks; chmod +x .githooks/* scripts/*.sh scripts/taiga/*.sh
           git remote add origin "$1" 2>/dev/null || git remote set-url origin "$1"
           git add -A; git commit -m "chore: estructura inicial" || true
           git push -u origin "$RAMA_PRINCIPAL" ;;
  tarea)   [ $# -ge 2 ] || { uso; exit 1; }
           id="$1"; shift; desc="$(echo "$*" | tr '[:upper:] ' '[:lower:]-' | tr -cd 'a-z0-9-')"
           git checkout "$RAMA_PRINCIPAL"; git pull --rebase; git checkout -b "feature/TG-${id}-${desc}" ;;
  commit)  [ $# -ge 1 ] || { uso; exit 1; }; git add -A; git commit -m "$1" ;;
  sync)    git fetch --all --prune; git pull --rebase ;;
  publicar) git push -u origin "$(git rev-parse --abbrev-ref HEAD)" ;;
  version) [ $# -eq 1 ] || { uso; exit 1; }; git tag -a "v$1" -m "Versión $1"; git push origin "v$1" ;;
  cambios) git fetch origin "$RAMA_PRINCIPAL" -q
           git diff --name-only "HEAD...origin/${RAMA_PRINCIPAL}" ;;
  estado)  git status -sb; git log --oneline -5 ;;
  *)       uso; exit 1 ;;
esac
