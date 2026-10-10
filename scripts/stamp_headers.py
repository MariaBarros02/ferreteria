#!/usr/bin/env python3
# @@ Estampa la cabecera estándar (nombre, descripción, fechas, autor, versión) en cada artefacto.
"""Reemplaza la primera línea '@@ descripción' de cada archivo por la cabecera normalizada.

Uso: python3 scripts/stamp_headers.py <raíz>
Los archivos sin la marca '@@' se dejan intactos (idempotente).
"""
import os, sys, re

AUTOR = os.environ.get("AUTOR", "Equipo Ferretería")
FECHA = os.environ.get("FECHA", "2026-10-07")
VERSION = os.environ.get("VERSION", "1.0.0")

def bloque(nombre, desc):
    return [("Nombre", nombre), ("Descripción", desc), ("Fecha de creación", FECHA),
            ("Fecha de actualización", FECHA), ("Autor", AUTOR), ("Versión", VERSION)]

def render(ext, nombre, desc):
    f = bloque(nombre, desc)
    if ext in (".java", ".js", ".jsx", ".mjs", ".css"):
        return "/*\n" + "".join(f" * {k}: {v}\n" for k, v in f) + " */\n"
    if ext in (".sql",):
        return "-- " + "=" * 68 + "\n" + "".join(f"-- {k}: {v}\n" for k, v in f) + "-- " + "=" * 68 + "\n"
    if ext in (".sh", ".py", ".properties", ".yml", ".yaml", ".env", ".example", ".conf") or nombre in ("Dockerfile", "pre-push", "post-merge", "pre-commit", ".gitignore"):
        return "#" * 70 + "\n" + "".join(f"# {k}: {v}\n" for k, v in f) + "#" * 70 + "\n"
    if ext in (".xml", ".html"):
        return "<!--\n" + "".join(f"  {k}: {v}\n" for k, v in f) + "-->\n"
    if ext == ".md":
        return "<!--\n" + "".join(f"  {k}: {v}\n" for k, v in f) + "-->\n\n"
    return None

def main(raiz):
    for d, dirs, files in os.walk(raiz):
        dirs[:] = [x for x in dirs if x not in ("node_modules", ".git", "target", "dist")]
        for n in files:
            p = os.path.join(d, n)
            try:
                txt = open(p, encoding="utf-8").read()
            except Exception:
                continue
            m = re.search(r"^@@ (.*)$", txt, re.M)
            if not m or txt.find("@@ ") > 400 and not txt.lstrip().startswith("@@"):
                if not m: continue
            ext = os.path.splitext(n)[1]
            desc = m.group(1).strip()
            h = render(ext, n, desc)
            if h is None:
                continue
            lineas = txt.split("\n")
            idx = next(i for i, l in enumerate(lineas) if l.startswith("@@ "))
            previo = "\n".join(lineas[:idx])           # p.ej. shebang o <?xml ?>
            resto = "\n".join(lineas[idx + 1:])
            if ext in (".java", ".js", ".jsx", ".mjs", ".css") and previo.strip():
                nuevo = h + previo + "\n" + resto          # cabecera antes de 'package'/'import'
            else:
                nuevo = (previo + "\n" if previo else "") + h + resto
            open(p, "w", encoding="utf-8").write(nuevo)
            print("cabecera:", p)

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
