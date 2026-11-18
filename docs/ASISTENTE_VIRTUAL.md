<!--
  Nombre: ASISTENTE_VIRTUAL.md
  Descripción: Diseño, uso, ampliación y pruebas del asistente virtual de la plataforma (RF-24): motor de reglas determinista con árbol de decisión de preguntas frecuentes y consultas de solo lectura de precio, existencias y tasas de cambio.
  Fecha de creación: 2026-11-17
  Fecha de actualización: 2026-11-17
  Autor: Equipo Ferretería
  Versión: 1.0.0
-->

# Asistente virtual (RF-24)

Chatbot de soporte integrado en la aplicación. Responde las preguntas frecuentes sobre cómo usar cada módulo y consulta datos reales de solo lectura: el precio vigente y las existencias de un producto, y las tasas de cambio vigentes.

## 1. Decisión de diseño: reglas deterministas, no IA externa

| Criterio | Motor de reglas (elegido) | API de IA |
|---|---|---|
| Datos fuera del sistema | Ninguno | El texto (y a veces datos del negocio) viaja a un tercero |
| Secretos y costos | No requiere claves ni pago por uso | Clave de API que proteger y costo por consulta |
| Exactitud | Respuestas escritas a partir de la implementación real (`docs/MODULOS.md`) | Puede inventar pasos o reglas que no existen |
| Pruebas | La misma pregunta da siempre la misma respuesta: se prueba con aserciones exactas | Respuestas variables, difíciles de verificar |
| Permisos | Conoce los roles de cada tema (RF-09) | Habría que controlarlos fuera del modelo |

La lógica está aislada en `MotorAsistente` y `AsistenteService`. Si en el futuro se quiere un modelo de lenguaje para las preguntas sin coincidencia, se puede conectar en el punto de "no entendido" sin cambiar la API ni la interfaz.

## 2. Arquitectura

```
Barra superior (ícono robot) ──► AsistenteVirtual.jsx (panel lateral, conversación en memoria)
                                        │  GET  /api/asistente/inicio
                                        │  POST /api/asistente/mensajes { texto } | { nodo }
                                        ▼
                     AsistenteController ──► AsistenteService ──► MotorAsistente (clasificación)
                                                   │                    ▲
                                                   │                    └─ base-conocimiento.json (árbol de decisión)
                                                   ├─► ProductoService (seleccionPorCodigo, buscarParaSeleccion) — solo lectura
                                                   └─► MonedaService (listar) — solo lectura
```

| Archivo | Función |
|---|---|
| `backend/src/main/resources/asistente/base-conocimiento.json` | 45 temas en español e inglés: título, respuesta, palabras clave, subtemas, roles y enlace |
| `backend/.../service/BaseConocimiento.java` | Modelo del árbol (records) |
| `backend/.../service/MotorAsistente.java` | Valida el árbol al arrancar; normaliza y clasifica las preguntas |
| `backend/.../service/AsistenteService.java` | Orquesta la respuesta: temas, consultas de datos, permisos e idioma |
| `backend/.../controller/AsistenteController.java` | API REST (cualquier rol autenticado) |
| `frontend/src/components/AsistenteVirtual.jsx` | Panel del chat; el botón de acceso está en `Layout.jsx` |

## 3. Cómo decide la respuesta

1. **Tema elegido** (`nodo`): se responde ese tema. Un id inexistente devuelve 404.
2. **Código de barras** (8 a 14 dígitos en el texto): precio vigente y existencias de ese producto activo.
3. **Clasificación por palabras clave.** El texto se normaliza: minúsculas, sin tildes ni signos, sin palabras vacías ("de", "la", "the"…), comparando raíces simples ("registro" ≈ "registrar", "precios" ≈ "precio"). Cada frase clave suma tantos puntos como palabras tenga; las del otro idioma valen el 80 %.
   - **Tema específico** (frase de dos o más palabras, puntaje ≥ 2): se responde de inmediato.
4. **Consulta de dato**, solo si la pregunta no es de procedimiento ("cómo", "registrar", "aprobar"…):
   - **Tasa:** menciona una moneda ("dólar", "euros", "BRL") o pide una tasa ("tasa de cambio", "TRM"), y no habla de vender o comprar.
   - **Producto:** pide precio o existencias ("precio", "cuánto", "hay", "stock") y nombra algo. Se busca la frase, luego cada palabra y luego su singular; se muestran como máximo 3 productos.
5. **Tema más probable** (puntaje ≥ 1). Si varios temas empatan, se pide elegir entre ellos (`ACLARACION`).
6. **Sin coincidencia** (`NO_ENTENDIDO`): se sugieren los temas parciales o el menú principal.

**Permisos.** Los temas principales se filtran por rol. Si un tema describe una operación que el rol no puede hacer, la respuesta se muestra igual (es informativa), con un aviso de qué roles la realizan y **sin enlace**. Un enlace solo se ofrece si el rol puede abrir esa pantalla.

**Idioma.** Se responde según `Accept-Language` (es/en), que el frontend envía con el idioma elegido.

## 4. API

```http
GET  /api/asistente/inicio
POST /api/asistente/mensajes   { "texto": "¿Cómo registro una venta?" }
POST /api/asistente/mensajes   { "nodo": "venta-registrar" }
```

Respuesta (los campos nulos se omiten):

```json
{
  "tipo": "RESPUESTA",
  "texto": "1. Abre Ventas → Nueva venta.\n2. …",
  "nodo": "venta-registrar",
  "opciones": [],
  "enlace": { "ruta": "/ventas/nueva", "texto": "Abrir Nueva venta" }
}
```

- `tipo`: `BIENVENIDA`, `RESPUESTA`, `PRODUCTO`, `TASA`, `ACLARACION` o `NO_ENTENDIDO`.
- **Validación:**
  - `texto` admite como máximo 300 caracteres y no puede contener `<` ni `>`; si no se cumple, responde 400.
  - `nodo` solo admite minúsculas, números y guiones; si no se cumple, responde 400.
  - Un mensaje vacío responde 422 `MENSAJE_VACIO`.
- **Seguridad:** requiere JWT. No se guarda el texto de las conversaciones y el asistente no modifica datos.

## 5. Cómo agregar o cambiar un tema

1. Edita `base-conocimiento.json`. Cada nodo tiene esta forma:

   ```json
   {
     "id": "venta-anticipo",
     "titulo":    { "es": "Anticipos", "en": "Advance payments" },
     "respuesta": { "es": "…", "en": "…" },
     "claves":    { "es": ["anticipo", "abono"], "en": ["advance payment", "deposit"] },
     "opciones": [],
     "roles": ["VENTAS", "ADMINISTRADOR"],
     "enlace": { "ruta": "/ventas", "texto": { "es": "Ir a Ventas", "en": "Go to Sales" } }
   }
   ```

   - `roles` vacío: el tema aplica a todos los roles.
   - `enlace.roles` es opcional: úsalo solo si la pantalla tiene permisos distintos a los del tema.
   - `"oculto": true`: el tema no aparece en los menús (como el saludo).
2. Agrega su `id` en las `opciones` del tema padre o en `raiz`.
3. Para que una pregunta llegue a ese tema, prefiere **frases de dos o más palabras** como palabras clave ("registrar venta"); las palabras sueltas solo sirven como respaldo.
4. Ejecuta las pruebas: `mvn test -Dtest=AsistenteServiceTest,MotorAsistenteTest`.

Al arrancar, el backend **valida el árbol**: ids válidos y únicos, opciones y temas principales existentes y respuesta en español. Si algo falla, la aplicación no inicia y el mensaje indica el nodo con problemas.

## 6. Pruebas

| Nivel | Archivo | Casos |
|---|---|---|
| Unitaria (motor) | `backend/src/test/java/.../unit/MotorAsistenteTest.java` | UT-MAS-01 … UT-MAS-11: validación del árbol, normalización, comparación de palabras, puntajes, empates y permisos |
| Unitaria (servicio) | `backend/src/test/java/.../unit/AsistenteServiceTest.java` | UT-ASI-01 … UT-ASI-21: base real, bienvenida por rol, preguntas frecuentes, avisos de permiso, códigos de barras, búsqueda por nombre, tasas, aclaraciones, inglés y errores |
| Integración (API + PostgreSQL) | `backend/src/test/java/.../integration/AsistenteApiIT.java` | IT-ASI-01 … IT-ASI-06: 401 sin sesión, temas por rol, español/inglés, permisos, producto real por código y validaciones 400/404/422 |
| Componente (frontend) | `frontend/src/__tests__/asistente.test.jsx` | FE-ASI-01 … FE-ASI-09: bienvenida, envío, temas, enlaces, errores, sin conexión, reinicio, límite de 300 caracteres y botón de la barra superior |
| Aceptación E2E | `frontend/e2e/asistente.spec.js` | E2E-ASI-01 … E2E-ASI-04: flujo completo, producto del seed, aviso para Gerencia y uso en móvil sin desplazamiento horizontal |

## 7. Limitaciones conocidas

- La clasificación se basa en palabras clave, no en comprensión del lenguaje. Una pregunta con palabras muy distintas a las previstas recibe sugerencias en lugar de respuesta; se corrige agregando palabras clave al tema.
- La búsqueda de productos usa los nombres tal como están en el catálogo (en español): "price of the drill" no encuentra "Taladro".
- No hay memoria de conversación en el servidor: cada mensaje se responde por sí solo. El panel solo muestra el historial de la sesión actual.
- Solo español e inglés, igual que el resto de la interfaz.
