#!/usr/bin/env python3
######################################################################
# Nombre: metricas.py
# Descripción: Evalúa y monitorea las métricas de metrics/metricas.json (Taiga + CI + cobertura): semáforo, reporte JSON/Markdown y alertas en Taiga.
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
"""Uso: python3 scripts/taiga/metricas.py [--sin-taiga] [--alertar] [--salida reports]

Con --sin-taiga solo evalúa las métricas de CI y cobertura (útil sin conectividad).
Las métricas de ámbito 'individual' sirven para apoyar y equilibrar la carga, no para clasificar personas.
"""
import argparse, datetime as dt, glob, json, os, statistics, sys, xml.etree.ElementTree as ET

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def _fecha(s):
    return dt.datetime.fromisoformat(s.replace("Z", "+00:00")) if s else None


def semaforo(valor, m):
    """Devuelve 'verde' | 'amarillo' | 'rojo' | 'sin_datos' según el sentido de la métrica."""
    if valor is None:
        return "sin_datos"
    if m["sentido"] == "mayor_mejor":
        return "verde" if valor >= m["verde"] else "amarillo" if valor >= m["amarillo"] else "rojo"
    return "verde" if valor <= m["verde"] else "amarillo" if valor <= m["amarillo"] else "rojo"


def leer_corridas(ruta, desde):
    if not os.path.exists(ruta):
        return []
    salida = []
    for l in open(ruta, encoding="utf-8"):
        try:
            r = json.loads(l)
        except ValueError:
            continue
        if _fecha(r["fecha"]) >= desde:
            salida.append(r)
    return salida


def metricas_ci(corridas):
    if not corridas:
        return {"M01": None, "M12": None}
    ok = sum(1 for c in corridas if c["resultado"] == "ok")
    return {"M01": round(ok / len(corridas) * 100, 1), "M12": round(statistics.mean(c["segundos"] for c in corridas) / 60, 1)}


def metricas_issues(issues, ahora, desde):
    abiertas = [i for i in issues if not i.get("is_closed")]
    cerradas = [i for i in issues if i.get("is_closed") and i.get("finished_date") and _fecha(i["finished_date"]) >= desde]
    horas = [(_fecha(i["finished_date"]) - _fecha(i["created_date"])).total_seconds() / 3600 for i in cerradas]
    edades = [(ahora - _fecha(i["created_date"])).days for i in abiertas]
    escapados = [i for i in issues if "produccion" in (_tags(i)) and _fecha(i["created_date"]) >= desde]
    por_persona = {}
    for i in cerradas:
        k = i.get("assigned_to_extra_info", {}) or {}
        nombre = k.get("full_name_display") or k.get("username") or "sin asignar"
        por_persona[nombre] = por_persona.get(nombre, 0) + 1
    return {"M02": len(abiertas), "M03": round(statistics.median(horas), 1) if horas else None,
            "M04": max(edades) if edades else 0, "M08": len(escapados)}, por_persona


def _tags(i):
    return [t[0] if isinstance(t, list) else t for t in (i.get("tags") or [])]


def metricas_tareas(tareas, desde):
    por = {}
    for t in tareas:
        if not (t.get("is_closed") and t.get("finished_date") and _fecha(t["finished_date"]) >= desde):
            continue
        e = t.get("assigned_to_extra_info") or {}
        nombre = e.get("full_name_display") or e.get("username") or "sin asignar"
        d = (_fecha(t["finished_date"]) - _fecha(t["created_date"])).total_seconds() / 86400
        por.setdefault(nombre, []).append(d)
    return {n: {"tareas_cerradas": len(v), "ciclo_mediana_dias": round(statistics.median(v), 1)} for n, v in por.items()}


def cobertura_jacoco(ruta):
    if not os.path.exists(ruta):
        return None
    for c in ET.parse(ruta).getroot().findall("counter"):
        if c.get("type") == "LINE":
            cub, per = int(c.get("covered")), int(c.get("missed"))
            return round(cub / (cub + per) * 100, 1) if cub + per else None
    return None


def cobertura_vitest(ruta):
    if not os.path.exists(ruta):
        return None
    return json.load(open(ruta))["total"]["lines"]["pct"]


def mutacion_pit(patron):
    archivos = sorted(glob.glob(patron, recursive=True))
    if not archivos:
        return None
    muts = ET.parse(archivos[-1]).getroot().findall("mutation")
    return round(sum(1 for m in muts if m.get("status") == "KILLED") / len(muts) * 100, 1) if muts else None


def evaluar(defs, valores, individual=None):
    filas = []
    for m in defs["metricas"]:
        v = valores.get(m["id"])
        filas.append({**m, "valor": v, "estado": semaforo(v, m)})
    return {"generado": dt.datetime.now(dt.timezone.utc).isoformat(), "ventana_dias": defs["ventana_dias"],
            "metricas": filas, "individual": individual or {}}


def a_markdown(r):
    icono = {"verde": "🟢", "amarillo": "🟡", "rojo": "🔴", "sin_datos": "⚪"}
    l = [f"# Reporte de métricas ({r['generado'][:10]}, ventana {r['ventana_dias']} días)", "",
         "| ID | Métrica | Valor | Umbral verde | Estado |", "|---|---|---|---|---|"]
    for m in r["metricas"]:
        v = "—" if m["valor"] is None else f"{m['valor']} {m['unidad']}"
        l.append(f"| {m['id']} | {m['nombre']} | {v} | {m['verde']} | {icono[m['estado']]} {m['estado']} |")
    if r["individual"]:
        l += ["", "## Contribución por persona (para balancear carga y apoyar; no es un ranking)", "",
              "| Persona | Tareas cerradas | Ciclo mediano (días) | Errores resueltos |", "|---|---|---|---|"]
        for n, d in sorted(r["individual"].items()):
            l.append(f"| {n} | {d.get('tareas_cerradas', 0)} | {d.get('ciclo_mediana_dias', '—')} | {d.get('errores_resueltos', 0)} |")
    return "\n".join(l) + "\n"


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--sin-taiga", action="store_true"); p.add_argument("--alertar", action="store_true")
    p.add_argument("--salida", default=os.path.join(RAIZ, "reports")); a = p.parse_args(argv)
    defs = json.load(open(os.path.join(RAIZ, "metrics", "metricas.json"), encoding="utf-8"))
    ahora = dt.datetime.now(dt.timezone.utc); desde = ahora - dt.timedelta(days=defs["ventana_dias"])
    estado = os.environ.get("CI_STATE_DIR", os.path.join(RAIZ, ".ci-state"))
    valores = metricas_ci(leer_corridas(os.path.join(estado, "runs.jsonl"), desde))
    valores["M09"] = cobertura_jacoco(os.path.join(RAIZ, "backend/target/site/jacoco/jacoco.xml"))
    valores["M10"] = cobertura_vitest(os.path.join(RAIZ, "frontend/coverage/coverage-summary.json"))
    valores["M11"] = mutacion_pit(os.path.join(RAIZ, "backend/target/pit-reports/**/mutations.xml"))
    individual, cliente = {}, None
    if not a.sin_taiga:
        sys.path.insert(0, os.path.dirname(__file__))
        from taiga_client import cliente_desde_entorno
        cliente = cliente_desde_entorno()
        v_i, errores_por_persona = metricas_issues(cliente.listar("issues"), ahora, desde)
        valores.update(v_i)
        individual = metricas_tareas(cliente.listar("tasks"), desde)
        for n, c in errores_por_persona.items():
            individual.setdefault(n, {})["errores_resueltos"] = c
    r = evaluar(defs, valores, individual)
    os.makedirs(a.salida, exist_ok=True)
    base = os.path.join(a.salida, f"metricas-{ahora:%Y-%m-%d}")
    json.dump(r, open(base + ".json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    open(base + ".md", "w", encoding="utf-8").write(a_markdown(r))
    print(a_markdown(r))
    rojos = [m for m in r["metricas"] if m["estado"] == "rojo"]
    if a.alertar and cliente and rojos and defs["alertas"]["crear_issue_en_rojo"]:
        from taiga_client import ids_notificar
        cuerpo = "Métricas en rojo:\n" + "\n".join(f"- **{m['id']} {m['nombre']}**: {m['valor']} {m['unidad']} (umbral verde {m['verde']})" for m in rojos)
        i = cliente.crear_issue(f"[Métricas] {len(rojos)} métrica(s) en rojo — {ahora:%Y-%m-%d}", cuerpo, ids_notificar(), None, [defs["alertas"]["etiqueta"]])
        print(f"Alerta creada: issue #{i['ref']}")
    return 1 if rojos else 0


if __name__ == "__main__":
    sys.exit(main())
