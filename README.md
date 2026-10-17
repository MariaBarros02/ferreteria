<!--
  Nombre: README.md
  Descripción: Guía del proyecto: arquitectura, puesta en marcha, módulos, seguridad, pruebas, CI/CD (GitHub Actions y Taiga), métricas y alcance/limitaciones.
  Fecha de creación: 2026-10-07
  Fecha de actualización: 2026-10-16
  Autor: Equipo Ferretería
  Versión: 1.1.0
-->

# Sistema de inventario de ferretería

> **¿Primera vez? Siga [`docs/GUIA_INICIO.md`](docs/GUIA_INICIO.md)**: instalación paso a paso, pruebas, despliegue, GitHub Actions y conexión con Taiga.
> Requisitos, matriz de trazabilidad y pendientes: [`docs/3_levantamiento_consolidado.md`](docs/3_levantamiento_consolidado.md) (secciones 9–11). Funcionamiento de cada módulo: [`docs/MODULOS.md`](docs/MODULOS.md).

Backend **Spring Boot 3 / Java 21 / JPA / Lombok / PostgreSQL** (API REST JSON, JWT, i18n es/en, OpenAPI) y frontend **React 18 / Material UI / Formik + Yup** (responsivo, accesible, i18n, sesión persistente), construidos a partir de [`docs/6_analisis_diseno_ferreteria.md`](docs/6_analisis_diseno_ferreteria.md) (análisis y diseño, versión 1.1).

```
database/   DDL, seed, pruebas SQL, verificaciones, init-db.sh y migrations/ (Flyway; única fuente del esquema)
backend/    API REST (controller → service → repository), seguridad JWT, i18n, OpenAPI, pruebas JUnit
frontend/   SPA React, pruebas Vitest/Playwright/Selenium, Dockerfile/nginx
scripts/    build-test-*.sh, repo-comandos.sh, ci-pipeline.sh, deploy.sh, taiga/ (issues, métricas)
tests/      k6 (carga, estrés, límite, concurrencia) y pruebas de los scripts Python
docs/       3_levantamiento_consolidado.md (requisitos + trazabilidad), 6_analisis_diseno_ferreteria.md (análisis y diseño), MODULOS.md, GUIA_INICIO.md, CASOS_DE_PRUEBA.md, METRICAS.md
.github/    workflows/ci.yml (BD, backend, frontend, E2E) y pr-automatico.yml
metrics/    metricas.json (definición de métricas)
```

## Puesta en marcha (desarrollo)
```bash
# 1) Base de datos (PostgreSQL local)  — alternativa: dejar que Flyway cree el esquema al arrancar el backend
database/init-db.sh
# 2) Backend  (perfil dev: JWT de ejemplo y contraseña demo para los usuarios del seed)
cd backend && SPRING_PROFILES_ACTIVE=dev DB_USER=postgres DB_PASSWORD=postgres mvn spring-boot:run
#    Swagger UI: http://localhost:8080/swagger-ui.html
# 3) Frontend
cd frontend && npm install && npm run dev        # http://localhost:5173
```
Usuarios del seed (clave demo `Ferreteria2026*` solo en perfil dev): `laura.ventas@`, `carlos.compras@`, `marta.inventario@`, `admin@`, `gerencia@` … `@ferreteria.com` (ver `database/02_seed.sql`). El seed solo trae precio vigente para el taladro: los demás productos se venden después de proponer su precio (Inventario) y aprobarlo (Gerencia) en **Precios** (RN-07).

**Producción**: `cp .env.example .env`, definir `DB_PASSWORD` y `JWT_SECRET` (≥ 32 caracteres, la app no arranca sin él), dejar `DEMO_PASSWORD` vacío y ejecutar `scripts/deploy.sh` (Docker Compose: db + backend + nginx/frontend).

## Qué implementa
| Área | Dónde |
|---|---|
| Módulos (RF-01 a RF-23) | Productos y presentaciones, terceros, **ventas** con selectores, IVA, descuentos, reservas, lotes FEFO y garantía; **detalle de venta**, **comprobante** imprimible y PDF; **compras** y **recepciones parciales** con lote y vencimiento; inventario por estado, alertas y baja por vencimiento; **precios** con aprobación; **devoluciones** de cliente y a proveedor; **ajustes**; **conteos físicos**; **cierres** mensuales; **auditoría** en la interfaz; **usuarios**; **monedas y tasas de cambio** (COP funcional; ventas y compras en USD, EUR y otras con equivalente en COP); **importación/exportación Excel**; lectores de código; consulta sin conexión. Detalle en [`docs/MODULOS.md`](docs/MODULOS.md). |
| Base de datos | Flyway `V1`–`V5` (compatibles con datos existentes, idempotentes); disparadores de inmutabilidad y no borrado; `03_pruebas.sql` (30 pruebas) y `04_verificaciones.sql` (saldo = suma de movimientos, lotes, devoluciones, totales). |
| Validaciones | Bean Validation en DTO (RN-01, RN-06, longitudes, anti `<>`), `@AssertTrue` entre campos, reglas en servicios y restricciones/disparadores de BD; en el cliente, Yup y validación de líneas. |
| Seguridad | JWT firmado, BCrypt, roles (RF-09) con `@PreAuthorize` en cada endpoint y menú/rutas por rol, bloqueo por fuerza bruta (429), CORS restringido, CSP/X-Frame/nosniff, errores sin trazas, auditoría sin datos sensibles, costos ocultos a roles sin permiso. |
| Atomicidad y concurrencia | `SELECT … FOR UPDATE` en orden de producto, transacción todo-o-nada (RN-11, RNF-02) y clave de idempotencia por operación. |
| i18n | Backend: `Accept-Language` + `messages*.properties`; frontend: react-i18next (es/en, persistente). |
| Documentación | Javadoc, OpenAPI/Swagger, JSDoc (`npm run docs`); cabecera estándar en cada artefacto. |
| Frontend | MUI + tokens (contraste AA, claro/oscuro), menú lateral por módulos y rol, responsivo (escritorio y móvil), estados de carga/vacío/error, confirmaciones, “saltar al contenido”, carga diferida por ruta. |
| Pruebas | [`docs/CASOS_DE_PRUEBA.md`](docs/CASOS_DE_PRUEBA.md): 293 casos automatizados (JUnit, Testcontainers, Vitest, Playwright, Selenium, SQL, scripts). |
| CI/CD | GitHub Actions (`.github/workflows/`) y pipeline local con Taiga (`scripts/ci-pipeline.sh`). |
| Métricas | `docs/METRICAS.md` |

## CI/CD
**GitHub Actions** (detalle y configuración manual de GitHub en `docs/GUIA_INICIO.md`, sección 11): `CI` ejecuta base de datos, backend (`mvn verify`), frontend (Vitest, build, auditoría de dependencias de producción) y E2E (Playwright) en cada push a `main`/`feature/**`/`fix/**` y en cada PR hacia `main`; `PR automático` abre el PR al publicar una rama `feature/**` o `fix/**`. Permisos mínimos y acciones fijadas por SHA.

**Taiga** (pipeline local):
```bash
scripts/repo-comandos.sh init <url>           # repo + hooks (.githooks: pre-commit, pre-push)
scripts/repo-comandos.sh tarea 42 "ajuste"    # rama feature/TG-42-ajuste
scripts/ci-pipeline.sh --once                 # una revisión (cron: */5 * * * *)
scripts/ci-pipeline.sh --watch 60             # o en bucle
```
Configure en `.env` `TAIGA_URL/USER/PASSWORD/PROJECT_ID/NOTIFY_IDS` y, opcionalmente, `scripts/taiga/usuarios.json` (correo de git → id Taiga) para asignar la issue al autor del commit. Taiga envía los correos a observadores y asignados.

## Estado de verificación (importante)
Resultados obtenidos en el entorno de desarrollo de la auditoría del 2026-10-16 (sin acceso a Maven Central ni a Docker):
- **Base de datos:** instalación limpia en PostgreSQL 16 con `init-db.sh` (V1–V5 + seed): 30/30 pruebas SQL en éxito, 0 filas en las verificaciones de integridad; segunda aplicación de las migraciones sin errores.
- **Frontend:** `npm run build` correcto; **73/73** pruebas Vitest en verde; recorrido de las 24 rutas en Chromium (escritorio y 360 px) con API simulada **solo para esa comprobación local**, sin errores de ejecución ni desbordamiento horizontal.
- **Backend:** verificación de tipos con `javac` contra un classpath sin conexión (dependencias reales disponibles + stubs de Spring/JPA): 0 errores; **113/119** pruebas unitarias JUnit/Mockito en verde; las 6 restantes (importación Excel) fallan en ese entorno por la versión de Apache POI disponible, no por el código. **No se ha ejecutado `mvn verify`**: las pruebas de integración (Testcontainers) y el arranque real de Spring/Hibernate se validarán en la primera ejecución del workflow `CI`.
- **E2E Playwright, Selenium, k6, PIT y Stryker:** escritos y configurados (E2E en la CI), no ejecutados aquí.
- **GitHub:** los workflows se validaron con `actionlint`; todavía no se han ejecutado en GitHub ni se ha creado un PR desde este trabajo.

## Alcance y desviaciones respecto del análisis
- Pendientes (detalle en `docs/3_levantamiento_consolidado.md`, sección 11): **factura electrónica DIAN** (los documentos son *comprobantes internos* con leyenda explícita; `FacturacionPort` es el punto de integración), rastreo de **número de serie** individual, **anulación de ventas** (se corrige con devolución), carga automática de la **TRM** (las tasas de cambio se registran a mano con su fuente), recálculo del costo promedio al anular un ingreso y actualización de dependencias de desarrollo del frontend con avisos de seguridad (`npm audit` sin `--omit=dev`).
- El primer administrador de una base nueva se crea por SQL (ver la guía); los demás desde **Administración → Usuarios**.
- Consulta por código: `GET /api/productos/codigo/{codigo}` (el diseño usaba `/{codigoBarras}`, que colisiona con `/{id}`).
- El autor de las cabeceras es el marcador “Equipo Ferretería” y la fecha de creación de cada archivo: cámbielos con `AUTOR=... FECHA=... python3 scripts/stamp_headers.py` (solo afecta archivos nuevos con la marca `@@`) o con búsqueda/reemplazo.
- JWT en `localStorage` cuando se elige “mantener sesión” (permanencia): es vulnerable a XSS; se mitiga con CSP, validación anti-`<>` y sin `dangerouslySetInnerHTML`. Una alternativa más robusta es cookie `HttpOnly` + CSRF.
- El control de intentos fallidos es en memoria (una sola réplica).
- `.env.example` contenía credenciales de Taiga y un secreto JWT de ejemplo; se reemplazaron por marcadores. **Cambie la contraseña de Taiga y el secreto JWT** si se usaron en algún entorno: siguen en el historial de git del primer commit.
