<!--
  Nombre: GUIA_INICIO.md
  Descripción: Guía paso a paso: instalar y arrancar el proyecto en su computador, ejecutar pruebas, desplegar y conectar con Taiga.
  Fecha de creación: 2026-10-07
  Fecha de actualización: 2026-10-16
  Autor: Equipo Ferretería
  Versión: 1.1.0
-->

# Guía de inicio y operación

> Windows: los scripts `.sh` requieren **Git Bash** o **WSL**. Los comandos `mvn`, `npm` y `docker` funcionan igual en PowerShell.

## 0. Qué necesita instalar

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 21 | Backend |
| Maven | 3.9+ | Compilar/probar el backend |
| Node.js | 20 o 22 | Frontend |
| Docker (Desktop) | reciente | PostgreSQL sin instalarlo, pruebas `*IT` (Testcontainers) y despliegue |
| Git | cualquiera | Repositorio y hooks |
| Python | 3.9+ | Scripts de Taiga y métricas (sin librerías extra) |
| PostgreSQL 16 + `psql` | opcional | Solo si no usa Docker para la base de datos |
| k6 | opcional | Pruebas de carga/estrés |

Puertos que se usan: **5432** (PostgreSQL), **8080** (API), **5173** (frontend en desarrollo), **8081** (frontend con Docker Compose).

## 1. Preparar la carpeta y el repositorio

```bash
#LINUX
unzip ferreteria.zip && cd ferreteria
git init -b main
git config core.hooksPath .githooks     # activa los hooks pre-commit y pre-push
chmod +x scripts/*.sh scripts/taiga/*.sh .githooks/* database/init-db.sh

#WINDOWS 
Expand-Archive -Path .\ferreteria.zip -DestinationPath . -Force
Set-Location .\ferreteria

git init -b main
git config core.hooksPath .githooks

bash -c "chmod +x scripts/*.sh scripts/taiga/*.sh .githooks/* database/init-db.sh"
```
Para publicarlo en GitHub/GitLab: `scripts/repo-comandos.sh init <url-del-repositorio-remoto>`.

## 2. Base de datos

Elija **una** opción. Ambas dejan la base `ferreteria_db` con el esquema y los datos de prueba.

**Opción A (recomendada): PostgreSQL en Docker, sin instalar nada más**
```bash
#LINUX
docker run --name ferreteria-db -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16-alpine
docker exec -i ferreteria-db psql -U postgres -c "CREATE DATABASE ferreteria_db"
docker exec -i ferreteria-db psql -U postgres -d ferreteria_db -v ON_ERROR_STOP=1 < database/01_ddl.sql
docker exec -i ferreteria-db psql -U postgres -d ferreteria_db -v ON_ERROR_STOP=1 < database/02_seed.sql

#WINDOWS
# 1. Crear el contenedor
docker run --name ferreteria-db -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16-alpine

# 2. Crear la base de datos
docker exec -i ferreteria-db psql -U postgres -c "CREATE DATABASE ferreteria_db;"

# 3. Crear tablas y estructura
Get-Content .\database\01_ddl.sql -Raw | docker exec -i ferreteria-db psql -U postgres -d ferreteria_db -v ON_ERROR_STOP=1

# 4. Insertar datos iniciales
Get-Content .\database\02_seed.sql -Raw | docker exec -i ferreteria-db psql -U postgres -d ferreteria_db -v ON_ERROR_STOP=1
```
(En PowerShell reemplace `< archivo` por `Get-Content archivo | docker exec -i ...`.)

**Opción B: PostgreSQL ya instalado**
```bash
#LINUX
export PGHOST=localhost PGUSER=postgres PGPASSWORD=su_clave
database/init-db.sh        # crea la BD, ejecuta DDL + seed + pruebas de reglas (03) + verificaciones (04)

#GIT BASH
export PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres
bash database/init-db.sh
```

> Si prefiere no cargar el DDL a mano: cree solo la base vacía y arranque el backend; **Flyway** crea el esquema y los roles (`database/migrations`). Luego cargue `02_seed.sql` y reinicie el backend.

Las credenciales por defecto del backend (`postgres` / `postgres` en `localhost:5432`) coinciden con la Opción A. Si usa otras, defina `DB_URL`, `DB_USER` y `DB_PASSWORD` (ver `.env.example`).

## 3. Backend (API)

```bash
cd backend
#LINUX
mvn spring-boot:run -Dspring-boot.run.profiles=dev

#WINDOWS
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```
El perfil `dev` pone un secreto JWT de ejemplo y asigna la contraseña **`Ferreteria2026*`** a los usuarios del seed (que traen el marcador `hash_demo`). Compruebe:

- Salud: http://localhost:8080/actuator/health → `{"status":"UP"}`
- Documentación interactiva: http://localhost:8080/swagger-ui.html (botón *Authorize* con el token del login)
- Login por consola:
```bash
#LINUX
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"laura.ventas@ferreteria.com","password":"Ferreteria2026*"}'
  
#WINDOWS

$body = @{
    correo   = "laura.ventas@ferreteria.com"
    password = "Ferreteria2026*"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:8080/api/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
    
```

Usuarios del seed (todos con la clave demo): `laura.ventas@`, `carlos.compras@`, `marta.inventario@`, `admin@`, `gerencia@` + `ferreteria.com`. Rol de cada uno: Ventas, Compras, Inventario, Administrador, Gerencia.

## 4. Frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173  (el proxy de Vite envía /api al puerto 8080)
```

## 5. Comprobación rápida (5 minutos)

1. Entre como `marta.inventario@ferreteria.com` → **Productos**: debe ver el taladro Bosch. Cree un producto nuevo (código de 8–14 dígitos); repita el mismo código y verá el rechazo por duplicado (RN-01).
2. Cambie el idioma **ES/EN** (arriba) y el tema claro/oscuro.
3. Cierre sesión y entre como `laura.ventas@ferreteria.com` → **Ventas → Nueva venta**. Busque el cliente (“Juan”) y agregue el taladro escribiendo su nombre o su código `7701234500015` + Enter (así funciona también un lector de código de barras). Revise la vista previa y registre: se abre el detalle de la venta y desde allí **Ver comprobante** → imprimir o descargar PDF (comprobante interno, sin validez fiscal). Solo el taladro del seed tiene precio vigente aprobado; otro producto responderá "sin precio vigente" (RN-07) hasta que Inventario lo proponga en **Precios** y Gerencia lo apruebe.
4. Pida más unidades que el saldo → "Saldo insuficiente" y el inventario no cambia.
5. Entre como `carlos.compras@…` → **Compras → Nueva compra**, y luego como `marta.inventario@…` registre la **recepción** (parcial o total) desde el detalle de la compra.
6. Entre como `gerencia@…`: consulta ventas, **Auditoría** y **Cierres**, aprueba precios, pero no registra ventas.

El comportamiento de cada módulo (ventas, compras, lotes y vencimientos, precios, devoluciones, ajustes, conteos, reservas, cierres, auditoría, Excel, modo sin conexión y permisos) está en [`MODULOS.md`](MODULOS.md).

## 6. Ejecutar las pruebas

```bash
cd backend  && mvn test                  # unitarias (no necesitan Docker)
cd backend  && mvn verify                # + integración, sistema y seguridad (Docker encendido)
cd backend  && mvn org.pitest:pitest-maven:mutationCoverage     # mutación
cd frontend && npm test                  # unitarias y de componente (Vitest)
cd frontend && npm run build             # build de producción
cd frontend && npx playwright install chromium
cd frontend && npm run test:e2e          # E2E; requiere backend (perfil dev, con seed) y frontend corriendo
#                                          (criterios-aceptacion.spec.js + flujos-operativos.spec.js: compra → recepción → precio → venta → PDF → devolución)
database/init-db.sh                      # pruebas SQL de reglas de negocio + verificaciones de integridad
cd frontend && npm run test:selenium     # requiere Chrome instalado
python3 -m unittest tests/scripts/test_taiga_metricas.py
k6 run -e USER=laura.ventas@ferreteria.com -e PASS='Ferreteria2026*' tests/stress/carga.js

```
Todo junto: `scripts/build-test-backend.sh [--mutacion]` y `scripts/build-test-frontend.sh [--e2e] [--mutacion]`. La lista de casos está en `docs/CASOS_DE_PRUEBA.md`.

## 7. Despliegue con Docker Compose

```bash
cp .env.example .env     # defina DB_PASSWORD y JWT_SECRET (openssl rand -base64 32); opcional: EMPRESA_* para el comprobante
scripts/deploy.sh        # construye, levanta db + backend + frontend y espera /actuator/health
```
Frontend: http://localhost:8081 · API: http://localhost:8080.

**Primer usuario administrador (obligatorio en una base nueva).** Los demás usuarios se crean desde **Administración → Usuarios**, pero el primero debe existir para poder entrar, y en producción `DEMO_PASSWORD` debe estar vacío. Cree el primero por SQL con una contraseña con hash BCrypt:
```bash
HASH=$(docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'SuClaveSegura123' | tr -d ':\n' | sed 's/^\$2y/\$2a/')
docker compose exec -T db psql -U ferreteria -d ferreteria_db -c \
 "INSERT INTO usuario (nombre, correo, password_hash, id_rol) VALUES ('Administrador','admin@suempresa.com','$HASH',(SELECT id_rol FROM rol WHERE nombre='Administrador'));"
```
(Cambie `ferreteria` por su `DB_USER`.) Para entornos de prueba/staging puede cargar `02_seed.sql` y definir `DEMO_PASSWORD`; nunca en producción.

## 8. Conexión con Taiga

### 8.1 Cómo funciona
```
 git (rama main)            ci-pipeline.sh                     Taiga (API REST)
 ───────────────  fetch ─▶  ¿hay commits nuevos?
                            ¿qué carpetas cambiaron?
                            compila + prueba (backend/frontend)
                              ├─ TODO OK ─▶ deploy.sh (Docker Compose) ─▶ etiqueta deploy-FECHA
                              └─ HAY ERRORES ─▶ taiga_client.py reportar-fallo
                                                   │ 1. POST /auth  (usuario "bot")
                                                   │ 2. ¿ya hay una issue abierta para este mismo fallo?
                                                   │      sí → la comenta (no duplica)
                                                   │      no → POST /issues  (log, commit, rama, autor)
                                                   │ 3. POST /tasks × 3 (investigar, corregir, verificar)
                                                   └─ observadores + asignado = equipo + autor del commit
```
- Taiga es quien **envía los correos**: notifica a los *observadores* y al *asignado* de la issue/tareas según la configuración de notificaciones de cada usuario en Taiga.
- Si hay errores **no se despliega**. El commit se marca como ya procesado para no reportar en bucle.
- El "bot" es una cuenta de Taiga con usuario y contraseña (`type: normal`). Las credenciales viven solo en `.env` (ignorado por Git).
- Los comandos de Taiga **no leen `.env` por sí solos**; `ci-pipeline.sh` sí lo carga. Para ejecutarlos a mano: `set -a; . ./.env; set +a`.

### 8.2 Configuración paso a paso
1. **Proyecto**: cree el proyecto en Taiga y active los módulos **Issues** y **Backlog o Kanban** (para las tareas).
2. **Cuenta bot**: cree un usuario con contraseña (no social login), añádalo al proyecto con un rol que pueda *crear issues, crear tareas y comentar*.
3. **URL de la API**: `TAIGA_URL` es la dirección de la API, no la de la interfaz web. En una instalación propia es la de su servidor (p. ej. `https://taiga.miempresa.com`); en la versión alojada de Taiga suele ser `https://api.taiga.io`.
4. **ID del proyecto** (`TAIGA_PROJECT_ID`):
```bash
curl -s "$TAIGA_URL/api/v1/projects/by_slug?slug=SLUG-DE-SU-PROYECTO"     # el slug está en la URL del proyecto; busque "id"
```
5. **IDs de las personas a notificar** (`TAIGA_NOTIFY_IDS`, separados por coma):
```bash
TOKEN=$(curl -s -X POST "$TAIGA_URL/api/v1/auth" -H "Content-Type: application/json" \
        -d '{"type":"normal","username":"BOT","password":"CLAVE"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['auth_token'])")
curl -s -H "Authorization: Bearer $TOKEN" "$TAIGA_URL/api/v1/memberships?project=ID_PROYECTO"   # campo "user" = id; también trae el correo
```
6. **Archivo `.env`** (copiado de `.env.example`): `TAIGA_URL`, `TAIGA_USER`, `TAIGA_PASSWORD`, `TAIGA_PROJECT_ID`, `TAIGA_NOTIFY_IDS`.
7. **Asignar al autor del commit** (opcional): edite `scripts/taiga/usuarios.json` con `"correo-de-git": id_taiga`. El correo es el de `git config user.email` de cada persona.

### 8.3 Probar la conexión (en este orden)
```bash
set -a; . ./.env; set +a
# a) Conexión + permisos: debe imprimir "Creado #N" y verá la issue en Taiga
python3 scripts/taiga/taiga_client.py issue --asunto "Prueba de integración" --descripcion "Borrar luego"
# b) Simular un fallo de CI completo (issue + 3 tareas + observadores), sin tocar el código
echo "FAIL: prueba simulada" > /tmp/fallo.log
python3 scripts/taiga/taiga_client.py reportar-fallo --etapa backend --sha $(printf 'a%.0s' {1..40}) \
   --rama main --autor "Su Nombre" --correo su@correo.com --log /tmp/fallo.log
# c) Repita (b): ahora debe decir que la issue ya está abierta y solo agrega un comentario

#WINDOWS
# Cargar variables de entorno
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*?)\s*=\s*(.*)\s*$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim().Trim('"').Trim("'")
        Set-Item -Path "Env:$name" -Value $value
    }
}

# a) Prueba de conexión y permisos
python scripts/taiga/taiga_client.py issue --asunto "Prueba de integración" --descripcion "Borrar luego"

# b) Simular un fallo de CI
"FAIL: prueba simulada" | Set-Content -Path ".\fallo.log" -Encoding utf8
$SHA = "a" * 40

python scripts/taiga/taiga_client.py reportar-fallo --etapa backend --sha $SHA --rama main --autor "Su Nombre" --correo "su@correo.com" --log ".\fallo.log"

# c) Repetir para probar la deduplicación
python scripts/taiga/taiga_client.py reportar-fallo --etapa backend --sha $SHA --rama main --autor "Su Nombre" --correo "su@correo.com" --log ".\fallo.log"

```
Errores típicos: `401` = usuario/clave del bot; `403` = el rol del bot no puede crear issues/tareas; `400` en tareas = módulo Backlog/Kanban desactivado; no llegan correos = revise las notificaciones del usuario en Taiga y que sea miembro del proyecto.

### 8.4 Poner a trabajar el pipeline (servidor de integración)
El pipeline hace `git checkout main` y `merge --ff-only`, por eso debe correr en **un clon dedicado** (no en la carpeta donde usted programa) que tenga Java, Maven, Node, Docker y Python:
```bash
git clone <url> ~/ferreteria-ci && cd ~/ferreteria-ci
cp .env.example .env          # complete DB_*, JWT_SECRET y TAIGA_*
scripts/ci-pipeline.sh --once --forzar --sin-despliegue    # prueba: compila y prueba todo sin desplegar
scripts/ci-pipeline.sh --once                              # ciclo real (detecta cambios; despliega si todo pasa)
```
Automatícelo con cron cada 5 minutos: `*/5 * * * * cd ~/ferreteria-ci && scripts/ci-pipeline.sh --once >> .ci-state/cron.log 2>&1`, o déjelo en bucle con `scripts/ci-pipeline.sh --watch 60`. Resultados en `.ci-state/` (`runs.jsonl` historial, `logs/<etapa>.log`, `issues.json` fallos ya reportados).

Variables útiles: `RAMA_PRINCIPAL` (si su rama no es `main`), `CI_STATE_DIR`.

## 9. Métricas
```bash
set -a; . ./.env; set +a
python3 scripts/taiga/metricas.py --sin-taiga        # solo CI y cobertura, sin red
python3 scripts/taiga/metricas.py --alertar          # con Taiga; abre una issue si hay métricas en rojo
```
Informes en `reports/`. Definiciones y umbrales: `metrics/metricas.json`; detalle en `docs/METRICAS.md`.

## 10. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `app.jwt.secret debe definirse con al menos 32 caracteres` | Arrancó sin perfil `dev` y sin `JWT_SECRET`. Use `-Dspring-boot.run.profiles=dev` o defina la variable. |
| Login responde 401 con la clave demo | Falta el perfil `dev` (no se asignó la clave) o el seed no se cargó. Reinicie el backend con `dev`. |
| Login responde 429 | Cuenta bloqueada 15 min tras 5 fallos (3 en el perfil de pruebas). Reinicie el backend para limpiar el contador. |
| `Connection refused` a PostgreSQL | El contenedor no está activo (`docker ps`) o el puerto 5432 está ocupado. |
| `mvn verify` falla en los `*IT` | Docker no está corriendo (Testcontainers lo necesita). `mvn test` sí funciona sin Docker. |
| El frontend muestra errores de red / CORS | El backend no está en el 8080, o abre el frontend desde un origen distinto de `CORS_ORIGINS`. |
| Venta responde "sin precio vigente" | Ese producto no tiene precio aprobado; el seed solo trae el del taladro. Propóngalo en **Precios** (Inventario) y apruébelo con Gerencia. |
| "Periodo cerrado" al registrar un movimiento | El mes de la operación está cerrado; Gerencia puede reabrirlo en **Cierres** (queda auditado). |
| La importación de Excel rechaza el archivo | Use la plantilla descargada (encabezados exactos), formato `.xlsx`, máx. 5 MB y 5000 filas; un archivo idéntico ya importado no se reprocesa. |
| Sin conexión no aparecen productos | Debe haber sincronizado antes (se hace al iniciar sesión o con *Sincronizar para uso sin conexión*). |
| "No se permite versionar .env" al hacer commit | El hook `pre-commit` bloquea versionar secretos: `.env` ya está en `.gitignore`. |

## 11. GitHub Actions, ramas y Pull Requests

**Workflows** (`.github/workflows/`):

| Workflow | Se ejecuta en | Jobs (checks visibles en el PR) |
|---|---|---|
| `CI` (`ci.yml`) | `push` a `main`, `feature/**` y `fix/**`; `pull_request` hacia `main` (cada commit nuevo del PR); manual (`workflow_dispatch`) | **Base de datos (PostgreSQL 16)**: `init-db.sh` (DDL, migraciones, seed, 25 pruebas SQL, verificaciones) y reaplicación idempotente de las migraciones · **Backend (Maven verify)**: unitarias + integración con Testcontainers, JaCoCo · **Frontend (Vitest + build)**: pruebas, build y `npm audit --omit=dev` · **E2E (Playwright)**: backend real + frontend + Chromium (escritorio y móvil) |
| `PR automático` (`pr-automatico.yml`) | `push` a `feature/**` o `fix/**` | Crea el PR hacia `main` si la rama no tiene uno abierto. No fusiona ni hace commits (no hay bucles). |

Cada job publica un **resumen** en la ejecución (conteo de pruebas) y sube los reportes como artefactos (surefire/failsafe/JaCoCo, JUnit de Vitest, reporte HTML de Playwright y registros de backend/frontend si E2E falla).

**Seguridad de los workflows:** permisos por defecto `contents: read`; solo el job que crea PR tiene además `pull-requests: write`; se usa el `GITHUB_TOKEN` del propio repositorio (ningún token en archivos); el secreto JWT del E2E se genera en cada ejecución; las acciones están fijadas por SHA de commit (`actions/checkout` v4.2.2, `setup-java` v4.7.1, `setup-node` v4.4.0, `upload-artifact` v4.6.2); los workflows usan `pull_request` (no `pull_request_target`), así que el código de PR de forks no recibe secretos.

**Procedimiento de trabajo:**
```bash
git checkout main && git pull
git checkout -b feature/RF-xx-descripcion      # nunca trabajar directamente en main
# ... cambios + pruebas locales (npm test, mvn verify) ...
git push -u origin feature/RF-xx-descripcion   # dispara "PR automático" → crea el PR hacia main
# o, manualmente: gh pr create --base main --head feature/RF-xx-descripcion
```
En el PR, los checks de **CI** se ejecutan en cada commit. Fusione solo con todos en verde. Un commit hecho directamente en `main` no genera PR (ya está en el destino).

**Configuración manual en GitHub (la debe hacer el propietario del repositorio):**
1. *Settings → Actions → General → Workflow permissions*: deje “Read repository contents” y marque **“Allow GitHub Actions to create and approve pull requests”** (sin esto, `PR automático` falla con 403).
2. *Settings → Branches → Add branch ruleset / protection rule* para `main`: **Require a pull request before merging**, **Require status checks to pass** y seleccione `Base de datos (PostgreSQL 16)`, `Backend (Maven verify)`, `Frontend (Vitest + build)` y `E2E (Playwright)` (aparecen tras la primera ejecución), y **Block force pushes**.
3. Los eventos generados con `GITHUB_TOKEN` (como el PR que abre `PR automático`) no disparan otros workflows; por eso `CI` también se ejecuta en el `push` de las ramas `feature/**` y `fix/**`: sus checks quedan en el commit y aparecen en el PR desde el primer momento. Los commits siguientes ejecutan además el evento `pull_request`.

El pipeline local con Taiga (`scripts/ci-pipeline.sh`, sección 8) sigue disponible y es independiente de GitHub Actions.
