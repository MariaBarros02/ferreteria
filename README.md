<!--
  Nombre: README.md
  Descripción: Guía del proyecto: arquitectura, puesta en marcha, seguridad, pruebas, CI/CD con Taiga, métricas y alcance/limitaciones.
  Fecha de creación: 2026-10-07
  Fecha de actualización: 2026-10-07
  Autor: Equipo Ferretería
  Versión: 1.0.0
-->

# Sistema de inventario de ferretería

> **¿Primera vez? Siga [`docs/GUIA_INICIO.md`](docs/GUIA_INICIO.md)**: instalación paso a paso, pruebas, despliegue y conexión con Taiga.

Backend **Spring Boot 3 / Java 21 / JPA / Lombok / PostgreSQL** (API REST JSON, JWT, i18n es/en, OpenAPI) y frontend **React 18 / Material UI / Formik + Yup** (responsivo, accesible, i18n, sesión persistente), construidos a partir de `6_analisis_diseno_ferreteria.md`.

```
database/   DDL, seed, pruebas SQL, verificaciones, init-db.sh y migrations/ (Flyway; única fuente del esquema)
backend/    API REST (controller → service → repository), seguridad JWT, i18n, OpenAPI, pruebas JUnit
frontend/   SPA React, pruebas Vitest/Playwright/Selenium, Dockerfile/nginx
scripts/    build-test-*.sh, repo-comandos.sh, ci-pipeline.sh, deploy.sh, taiga/ (issues, métricas)
tests/      k6 (carga, estrés, límite, concurrencia) y pruebas de los scripts Python
docs/       CASOS_DE_PRUEBA.md, METRICAS.md
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
Usuarios del seed (clave demo `Ferreteria2026*` solo en perfil dev): `laura.ventas@`, `marta.inventario@`, `gerencia@`… `@ferreteria.com` (ver `database/02_seed.sql`). El seed no trae precios vigentes para todos los productos: una venta de un producto sin precio aprobado responde 422 (RN-07).

**Producción**: `cp .env.example .env`, definir `DB_PASSWORD` y `JWT_SECRET` (≥ 32 caracteres, la app no arranca sin él), dejar `DEMO_PASSWORD` vacío y ejecutar `scripts/deploy.sh` (Docker Compose: db + backend + nginx/frontend).

## Qué implementa
| Requisito | Dónde |
|---|---|
| CRUD REST + JSON | Productos, Terceros (CRUD), Ventas (alta/consulta), Inventario y Auditoría (lectura); unidades por producto |
| Validaciones | Bean Validation en DTO (RN-01, RN-06, longitudes, anti `<>`), `@AssertTrue` entre campos, reglas en servicios y restricciones/disparadores de BD |
| Seguridad | JWT firmado, BCrypt, roles (RF-09) con `@PreAuthorize`, bloqueo por fuerza bruta (429), CORS restringido, CSP/X-Frame/nosniff, rechazo de campos desconocidos, errores sin trazas, secreto obligatorio |
| Atomicidad y concurrencia | `SELECT … FOR UPDATE` en orden de producto + transacción todo-o-nada (RN-11, RNF-02) |
| i18n | Backend: `Accept-Language` + `messages*.properties`; frontend: react-i18next (es/en, persistente) |
| Documentación | Javadoc (`mvn javadoc:javadoc`), OpenAPI/Swagger, JSDoc (`npm run docs`); cabecera estándar (nombre, descripción, creación, actualización, autor, versión) en cada artefacto |
| Frontend | MUI + tokens (`src/theme/tokens.js`, contraste AA, claro/oscuro), responsivo, “saltar al contenido”, roles ARIA, Formik+Yup, sesión persistente con renovación automática |
| Pruebas | Ver `docs/CASOS_DE_PRUEBA.md` (134 casos automatizados + plan de estrés/límite) |
| CI/CD + Taiga | `scripts/ci-pipeline.sh`: detecta cambios → compila/prueba → despliega o crea issue + 3 tareas + notificación en Taiga |
| Métricas | `docs/METRICAS.md` |

## CI/CD y Taiga
```bash
scripts/repo-comandos.sh init <url>           # repo + hooks (.githooks: pre-commit, pre-push)
scripts/repo-comandos.sh tarea 42 "ajuste"    # rama feature/TG-42-ajuste
scripts/ci-pipeline.sh --once                 # una revisión (cron: */5 * * * *)
scripts/ci-pipeline.sh --watch 60             # o en bucle
```
Configure en `.env` `TAIGA_URL/USER/PASSWORD/PROJECT_ID/NOTIFY_IDS` y, opcionalmente, `scripts/taiga/usuarios.json` (correo de git → id Taiga) para asignar la issue al autor del commit. Taiga envía los correos a observadores y asignados.

## Estado de verificación (importante)
- **Frontend: verificado en este entorno** — `npm run build` correcto y 27 pruebas Vitest en verde; los scripts Python de Taiga/métricas: 11 pruebas en verde; sintaxis bash validada con `bash -n`.
- **Backend: NO compilado ni ejecutado aquí** (sin acceso a Maven Central ni Docker). Se revisó la sintaxis, pero puede haber errores de compilación o de ajuste fino (versiones, mapeos JPA de ENUM nativos, mensajes). Ejecute `cd backend && mvn verify` primero; los `*IT` requieren Docker.
- **Playwright, Selenium, k6, PIT y Stryker**: scripts y configuración escritos, no ejecutados aquí.
- **Cobertura del frontend** medida: ~30 % de líneas (las páginas CRUD no tienen pruebas de componente; los criterios de aceptación se cubren por E2E).

## Alcance y desviaciones respecto del análisis
- No implementados: **gestión de usuarios (alta/edición desde la app; el primer administrador se crea por SQL, ver la guía)**, compras/recepción, aprobación de precios, ajustes y conteos, devoluciones, cierres, reportes e integración real DIAN (hay un puerto `FacturacionPort` con adaptador simulado).
- Consulta por código: `GET /api/productos/codigo/{codigo}` (el diseño usaba `/{codigoBarras}`, que colisiona con `/{id}`).
- El autor de las cabeceras es el marcador “Equipo Ferretería” y la fecha 2026-10-07: cámbielos con `AUTOR=... FECHA=... python3 scripts/stamp_headers.py` (solo afecta archivos nuevos con la marca `@@`) o con búsqueda/reemplazo.
- JWT en `localStorage` cuando se elige “mantener sesión” (permanencia): es vulnerable a XSS; se mitiga con CSP, validación anti-`<>` y sin `dangerouslySetInnerHTML`. Una alternativa más robusta es cookie `HttpOnly` + CSRF.
- El control de intentos fallidos es en memoria (una sola réplica).
