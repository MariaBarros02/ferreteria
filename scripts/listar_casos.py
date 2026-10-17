#!/usr/bin/env python3
######################################################################
# Nombre: listar_casos.py
# Descripción: Regenera en docs/CASOS_DE_PRUEBA.md la tabla de casos automatizados extrayendo los IDs de los tests (Java, JS, Python).
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-16
# Autor: Equipo Ferretería
# Versión: 1.1.0
######################################################################
"""Uso: python3 scripts/listar_casos.py   (desde la raíz del repositorio)

Reconoce @DisplayName("ID descripción") en JUnit, it('ID ...') / test('ID ...') en Vitest/Playwright/Selenium
y las funciones test_* de unittest. Reemplaza el bloque entre <!-- CASOS:INICIO --> y <!-- CASOS:FIN -->.
"""
import glob, os, re

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOC = os.path.join(RAIZ, "docs", "CASOS_DE_PRUEBA.md")
ORDEN = ["Unitaria", "Unitaria / Componente (frontend)", "Unitaria (scripts)", "Integración / Sistema", "Sistema (BD real)", "Seguridad", "Aceptación E2E"]


def extraer():
    casos = []

    def add(patron, nivel, herramienta, rx):
        for p in glob.glob(os.path.join(RAIZ, patron), recursive=True):
            for m in re.finditer(rx, open(p, encoding="utf-8").read()):
                mid = re.match(r"([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+(.*)", m.group(1).strip())
                if mid:
                    casos.append((mid.group(1), nivel(p) if callable(nivel) else nivel, herramienta, mid.group(2), os.path.relpath(p, RAIZ)))

    add("backend/src/test/java/**/unit/*.java", "Unitaria", "JUnit 5 + Mockito", r'@DisplayName\("([^"]+)"\)')
    add("backend/src/test/java/**/integration/*IT.java",
        lambda p: "Seguridad" if "Seguridad" in p else "Sistema (BD real)" if "ReglasBase" in p else "Integración / Sistema",
        "JUnit 5 + Spring Test (MockMvc) + Testcontainers", r'@DisplayName\("([^"]+)"\)')
    add("frontend/src/__tests__/*.test.*", "Unitaria / Componente (frontend)", "Vitest + Testing Library", r"\bit\(\s*[\"']([^\"']+)[\"']")
    add("frontend/e2e/*.js", "Aceptación E2E", "Playwright", r"\btest\(\s*[\"']([^\"']+)[\"']")
    add("frontend/e2e-selenium/*.mjs", "Aceptación E2E", "Selenium WebDriver", r"\btest\('([^']+)'")
    for p in glob.glob(os.path.join(RAIZ, "tests/scripts/*.py")):
        for m in re.finditer(r"def (test_\w+)", open(p, encoding="utf-8").read()):
            casos.append(("PY-" + m.group(1)[5:].upper()[:24], "Unitaria (scripts)", "unittest", m.group(1)[5:].replace("_", " "), os.path.relpath(p, RAIZ)))
    return casos


def tabla(casos):
    out = [f"Total de casos automatizados: **{len(casos)}**.", ""]
    for n in ORDEN:
        sub = sorted(c for c in casos if c[1] == n)
        if sub:
            out += [f"### {n}", "", "| ID | Caso | Herramienta | Archivo |", "|---|---|---|---|"]
            out += [f"| {c[0]} | {c[3].replace('|', '/')} | {c[2]} | `{c[4]}` |" for c in sub] + [""]
    return "\n".join(out)


def main():
    txt = open(DOC, encoding="utf-8").read()
    nuevo = re.sub(r"(<!-- CASOS:INICIO -->\n).*?(<!-- CASOS:FIN -->)", lambda m: m.group(1) + tabla(extraer()) + "\n" + m.group(2), txt, flags=re.S)
    open(DOC, "w", encoding="utf-8").write(nuevo)
    print("Actualizado", DOC)


if __name__ == "__main__":
    main()
