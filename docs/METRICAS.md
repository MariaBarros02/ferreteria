<!--
  Nombre: METRICAS.md
  Descripción: Cómo definir, evaluar y monitorear métricas de desempeño, calidad y producción con Taiga y el pipeline CI.
  Fecha de creación: 2026-10-07
  Fecha de actualización: 2026-10-07
  Autor: Equipo Ferretería
  Versión: 1.0.0
-->

# Métricas: definición, evaluación y monitoreo

## 1. Definir
Las métricas viven en `metrics/metricas.json` (una entrada por métrica): `id`, `nombre`, `unidad`, `ambito` (`sistema` | `equipo` | `individual`), `sentido` (`mayor_mejor` | `menor_mejor`), `fuente`, `formula` y umbrales `verde` / `amarillo` (fuera de ellos = rojo). Para añadir una métrica: agregue la entrada y calcule su valor en `scripts/taiga/metricas.py` (funciones `metricas_*`); el semáforo, el reporte y las alertas la recogen sin más cambios.

| Grupo | Métricas | Fuente |
|---|---|---|
| Calidad del código | M09 cobertura backend, M10 cobertura frontend, M11 puntaje de mutación | JaCoCo, Vitest, PIT |
| Entrega continua | M01 éxito del pipeline, M12 duración media | `.ci-state/runs.jsonl` (lo escribe `ci-pipeline.sh`) |
| Manejo de errores | M02 abiertos, M03 MTTR, M04 antigüedad, M08 escapados a producción | Issues de Taiga |
| Producción del equipo | M05 tareas cerradas, M06 tiempo de ciclo, M07 errores resueltos (por persona) | Tareas/issues de Taiga |

## 2. Evaluar
```bash
export TAIGA_URL=... TAIGA_USER=... TAIGA_PASSWORD=... TAIGA_PROJECT_ID=...   # o .env
python3 scripts/taiga/metricas.py            # evalúa y escribe reports/metricas-AAAA-MM-DD.{json,md}
python3 scripts/taiga/metricas.py --sin-taiga   # solo CI + cobertura (sin red)
```
Salida: tabla con semáforo (verde/amarillo/rojo/sin datos) y, si hay datos de Taiga, la contribución por persona. El código de salida es `1` si alguna métrica está en rojo (útil para CI).

## 3. Monitorear
- **Alertas**: `python3 scripts/taiga/metricas.py --alertar` crea una issue en Taiga con etiqueta `metrica` y notifica a `TAIGA_NOTIFY_IDS` cuando hay métricas en rojo.
- **Periodicidad** (cron, lunes 7:00): `0 7 * * 1 cd /ruta/ferreteria && python3 scripts/taiga/metricas.py --alertar >> reports/cron.log 2>&1`
- **Errores en Taiga**: cada fallo del pipeline crea una issue (etiquetas `ci`, `automatico`, etapa) con 3 tareas y observadores; si el mismo fallo persiste se comenta en la issue abierta en lugar de duplicarla. Los errores de producción deben etiquetarse `produccion` para alimentar M08.

## 4. Uso responsable de las métricas individuales
M05–M07 muestran la contribución por persona para **repartir la carga y detectar bloqueos**, no para ordenar o sancionar: dependen del tamaño de las tareas, de quién recibe los errores difíciles y de cómo se asignan en Taiga. Se recomienda revisarlas junto con la persona y acordar los umbrales con el equipo antes de usarlas en evaluaciones formales.
