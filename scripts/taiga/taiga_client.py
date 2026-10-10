#!/usr/bin/env python3
######################################################################
# Nombre: taiga_client.py
# Descripción: Cliente mínimo de la API REST de Taiga (stdlib): autenticación, issues, tareas, comentarios y reporte de fallos del pipeline.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
"""Cliente Taiga y reporte automático de fallos de CI.

Variables de entorno: TAIGA_URL, TAIGA_USER, TAIGA_PASSWORD, TAIGA_PROJECT_ID, TAIGA_NOTIFY_IDS.
Taiga envía por correo las notificaciones a los *watchers* y al asignado de cada issue/tarea.
"""
import argparse, hashlib, json, os, sys, urllib.error, urllib.request

ESTADO_DIR = os.environ.get("CI_STATE_DIR", os.path.join(os.path.dirname(__file__), "..", "..", ".ci-state"))


class TaigaError(RuntimeError):
    """Error de comunicación con Taiga."""


class TaigaClient:
    """Cliente HTTP de Taiga con token Bearer."""

    def __init__(self, url, usuario, clave, proyecto, timeout=20):
        self.base = url.rstrip("/") + "/api/v1"
        self.proyecto = int(proyecto)
        self.timeout = timeout
        self.token = None
        self._usuario, self._clave = usuario, clave

    def _req(self, metodo, ruta, cuerpo=None, params=""):
        data = json.dumps(cuerpo).encode() if cuerpo is not None else None
        r = urllib.request.Request(f"{self.base}{ruta}{params}", data=data, method=metodo)
        r.add_header("Content-Type", "application/json")
        if self.token:
            r.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(r, timeout=self.timeout) as resp:
                txt = resp.read().decode()
                return json.loads(txt) if txt else {}
        except urllib.error.HTTPError as e:
            raise TaigaError(f"{metodo} {ruta} -> {e.code}: {e.read().decode()[:300]}") from e
        except urllib.error.URLError as e:
            raise TaigaError(f"No se pudo conectar con Taiga: {e.reason}") from e

    def login(self):
        r = self._req("POST", "/auth", {"type": "normal", "username": self._usuario, "password": self._clave})
        self.token = r["auth_token"]
        return self

    def crear_issue(self, asunto, descripcion, observadores=(), asignado=None, etiquetas=()):
        cuerpo = {"project": self.proyecto, "subject": asunto[:500], "description": descripcion,
                  "watchers": list(observadores), "tags": list(etiquetas)}
        if asignado:
            cuerpo["assigned_to"] = asignado
        return self._req("POST", "/issues", cuerpo)

    def crear_tarea(self, asunto, descripcion, observadores=(), asignado=None, etiquetas=()):
        cuerpo = {"project": self.proyecto, "subject": asunto[:500], "description": descripcion,
                  "watchers": list(observadores), "tags": list(etiquetas)}
        if asignado:
            cuerpo["assigned_to"] = asignado
        return self._req("POST", "/tasks", cuerpo)

    def comentar_issue(self, issue_id, texto):
        actual = self._req("GET", f"/issues/{issue_id}")
        return self._req("PATCH", f"/issues/{issue_id}", {"comment": texto, "version": actual["version"]})

    def issue_abierta(self, issue_id):
        try:
            i = self._req("GET", f"/issues/{issue_id}")
        except TaigaError:
            return False
        return not i.get("is_closed", False)

    def listar(self, recurso, **filtros):
        """Lista paginada completa de issues|tasks|userstories del proyecto."""
        pagina, salida = 1, []
        while True:
            qs = "&".join(f"{k}={v}" for k, v in {"project": self.proyecto, "page": pagina, **filtros}.items())
            r = self._req("GET", f"/{recurso}", params="?" + qs)
            if not r:
                break
            salida.extend(r)
            if len(r) < 30:   # tamaño de página por defecto de Taiga
                break
            pagina += 1
        return salida


def cliente_desde_entorno():
    faltan = [v for v in ("TAIGA_URL", "TAIGA_USER", "TAIGA_PASSWORD", "TAIGA_PROJECT_ID") if not os.environ.get(v)]
    if faltan:
        raise SystemExit(f"Faltan variables de entorno: {', '.join(faltan)}")
    return TaigaClient(os.environ["TAIGA_URL"], os.environ["TAIGA_USER"], os.environ["TAIGA_PASSWORD"],
                       os.environ["TAIGA_PROJECT_ID"]).login()


def ids_notificar():
    return [int(x) for x in os.environ.get("TAIGA_NOTIFY_IDS", "").split(",") if x.strip().isdigit()]


def mapa_usuarios():
    """Correo de git -> id de usuario de Taiga (archivo usuarios.json junto a este script)."""
    ruta = os.path.join(os.path.dirname(__file__), "usuarios.json")
    if not os.path.exists(ruta):
        return {}
    with open(ruta, encoding="utf-8") as f:
        return {k.lower(): v for k, v in json.load(f).items() if not k.startswith("//")}


def huella(etapa, texto_log):
    """Identifica el mismo fallo entre ejecuciones (etapa + líneas con FAIL/ERROR normalizadas)."""
    lineas = sorted({l.strip() for l in texto_log.splitlines()
                     if any(p in l for p in ("FAIL", "ERROR", "✗", "AssertionError"))})[:30]
    return hashlib.sha1((etapa + "|" + "\n".join(lineas)).encode()).hexdigest()[:16]


def construir_reporte(etapa, sha, rama, autor, correo, cola_log):
    """Devuelve (asunto, descripción_markdown, tareas[(asunto, descripción)])."""
    asunto = f"[CI] Falla en {etapa} — {rama}@{sha[:8]}"
    desc = (f"El pipeline detectó errores en la etapa **{etapa}**; el despliegue a producción fue **bloqueado**.\n\n"
            f"- Commit: `{sha}`\n- Rama: `{rama}`\n- Autor: {autor} <{correo}>\n\n"
            f"### Últimas líneas del log\n```\n{cola_log[-3500:]}\n```\n")
    tareas = [
        (f"Investigar causa raíz del fallo en {etapa} ({sha[:8]})", "Reproducir localmente con scripts/build-test-*.sh y documentar la causa."),
        (f"Corregir y añadir prueba de regresión ({etapa})", "Corregir el defecto y agregar/ajustar el caso de prueba que lo cubra (ver docs/CASOS_DE_PRUEBA.md)."),
        (f"Verificar pipeline en verde y desplegar ({etapa})", "Confirmar que el pipeline pasa; el despliegue se dispara automáticamente."),
    ]
    return asunto, desc, tareas


def reportar_fallo(args):
    t = cliente_desde_entorno()
    cola = open(args.log, encoding="utf-8", errors="replace").read() if args.log and os.path.exists(args.log) else ""
    asunto, desc, tareas = construir_reporte(args.etapa, args.sha, args.rama, args.autor, args.correo, cola)
    autor_id = mapa_usuarios().get((args.correo or "").lower())
    observadores = sorted(set(ids_notificar() + ([autor_id] if autor_id else [])))
    os.makedirs(ESTADO_DIR, exist_ok=True)
    ruta_est = os.path.join(ESTADO_DIR, "issues.json")
    estado = json.load(open(ruta_est)) if os.path.exists(ruta_est) else {}
    h = huella(args.etapa, cola)
    previo = estado.get(h)
    if previo and t.issue_abierta(previo):
        t.comentar_issue(previo, f"El fallo persiste en `{args.sha[:8]}` ({args.rama}).\n```\n{cola[-1500:]}\n```")
        print(f"Issue #{previo} ya abierta: se agregó comentario (sin duplicar).")
        return 0
    issue = t.crear_issue(asunto, desc, observadores, autor_id, ["ci", "automatico", args.etapa])
    estado[h] = issue["id"]
    for a, d in tareas:
        t.crear_tarea(f"{a} · issue #{issue['ref']}", f"{d}\n\nRelacionada con la issue #{issue['ref']}.", observadores, autor_id, ["ci"])
    json.dump(estado, open(ruta_est, "w"))
    print(f"Issue #{issue['ref']} creada con {len(tareas)} tareas; notificados: {observadores}")
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("reportar-fallo", help="Crea issue + tareas y notifica al equipo")
    for a in ("etapa", "sha", "rama", "autor", "correo"):
        f.add_argument(f"--{a}", required=True)
    f.add_argument("--log")
    i = sub.add_parser("issue", help="Crea una issue manual")
    i.add_argument("--asunto", required=True); i.add_argument("--descripcion", default="")
    ta = sub.add_parser("tarea", help="Crea una tarea manual")
    ta.add_argument("--asunto", required=True); ta.add_argument("--descripcion", default="")
    a = p.parse_args(argv)
    try:
        if a.cmd == "reportar-fallo":
            return reportar_fallo(a)
        t = cliente_desde_entorno()
        r = (t.crear_issue if a.cmd == "issue" else t.crear_tarea)(a.asunto, a.descripcion, ids_notificar())
        print(f"Creado #{r['ref']}")
        return 0
    except TaigaError as e:
        print(f"ERROR Taiga: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
