/*
 * Nombre: flujos-operativos.spec.js
 * Descripción: Pruebas E2E del flujo operativo completo (compra → recepción → precio con aprobación → venta con lector de código → comprobante PDF → devolución), venta en moneda extranjera con tasa de cambio, navegación por rol y consulta sin conexión. Requieren backend y base de datos con los datos semilla.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
import { test, expect } from "@playwright/test";

const CLAVE = process.env.E2E_PASSWORD || "Ferreteria2026*";
const TORNILLO = "7701234500022";   // producto semilla sin lote ni vencimiento

async function entrar(page, correo) {
  await page.goto("/login");
  await page.getByLabel(/correo electrónico/i).fill(correo);
  await page.getByLabel(/^contraseña/i).fill(CLAVE);
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page.getByRole("heading", { name: /productos/i })).toBeVisible({ timeout: 10000 });
}

async function salir(page) {
  await page.getByRole("button", { name: /cerrar sesión/i }).click();
  await expect(page.getByRole("heading", { name: /iniciar sesión/i })).toBeVisible();
}

async function elegir(page, etiqueta, texto, opcion) {
  await page.getByRole("combobox", { name: etiqueta }).fill(texto);
  await page.getByRole("option", { name: opcion }).first().click();
}

async function escanear(page, etiqueta, codigo) {
  const campo = page.getByRole("combobox", { name: etiqueta });
  await campo.fill(codigo);
  await campo.press("Enter");
}

test.describe.serial("Flujo operativo completo", () => {
  // El flujo crea datos: se ejecuta una sola vez (proyecto de escritorio).
  test.skip(({ isMobile }) => isMobile, "El flujo de datos se valida en escritorio; móvil se cubre con E2E-RES-01");
  let compraUrl;
  const precio = String(1000 + Math.floor(Math.random() * 8000));

  test("E2E-FLU-01 Compras registra una orden con proveedor y producto por código", async ({ page }) => {
    await entrar(page, "carlos.compras@ferreteria.com");
    await page.goto("/compras/nueva");
    await elegir(page, /^proveedor/i, "Distribuidora", /Distribuidora Bosch/i);
    await escanear(page, /^producto/i, TORNILLO);
    await page.getByLabel("Cantidad 1").fill("50");
    await page.getByLabel("Costo unitario 1").fill("200");
    await page.getByRole("button", { name: /registrar compra/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: /registrar compra/i }).click();
    await expect(page.getByRole("heading", { name: /^compra OC-/i })).toBeVisible();
    compraUrl = page.url();
    await expect(page.getByText("Pendiente", { exact: true }).first()).toBeVisible();
  });

  test("E2E-FLU-02 Inventario recibe parcialmente y luego completa la orden", async ({ page }) => {
    await entrar(page, "marta.inventario@ferreteria.com");
    await page.goto(compraUrl);
    for (const [cantidad, factura] of [["20", `F-${Date.now()}`], ["30", `F-${Date.now() + 1}`]]) {
      await page.getByRole("button", { name: /registrar recepción/i }).click();
      const dialogo = page.getByRole("dialog");
      await dialogo.getByLabel(/factura del proveedor/i).fill(factura);
      await dialogo.getByLabel(/cantidad a recibir/i).first().fill(cantidad);
      await dialogo.getByRole("button", { name: /registrar recepción/i }).click();
      await expect(page.getByText("Recepción registrada")).toBeVisible();
    }
    await expect(page.getByText("Recibida", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /registrar recepción/i })).toHaveCount(0);
  });

  test("E2E-FLU-03 Inventario propone precio manual y Gerencia lo aprueba (sin autoaprobación)", async ({ page }) => {
    await entrar(page, "marta.inventario@ferreteria.com");
    await page.goto("/precios");
    await page.getByRole("button", { name: /nueva propuesta/i }).click();
    const dialogo = page.getByRole("dialog");
    await escanear(page, /^producto/i, TORNILLO);
    await dialogo.getByLabel(/cálculo/i).click();
    await page.getByRole("option", { name: "Manual" }).click();
    await dialogo.getByLabel(/precio propuesto/i).fill(precio);
    await dialogo.getByLabel(/justificación/i).fill("Precio de lanzamiento E2E");
    await dialogo.getByRole("button", { name: /proponer/i }).click();
    await expect(page.getByText(/propuesta creada/i)).toBeVisible();
    await salir(page);

    await entrar(page, "gerencia@ferreteria.com");
    await page.goto("/precios");
    const fila = page.getByRole("row", { name: /Tornillo/ }).filter({ hasText: "Precio de lanzamiento E2E" }).first();
    await fila.getByRole("button", { name: /aprobar/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: /aprobar/i }).click();
    await expect(page.getByText(/precio aprobado/i)).toBeVisible();
  });

  test("E2E-FLU-04 Ventas vende con lector de código, ve el detalle, el comprobante interno y descarga el PDF", async ({ page }) => {
    await entrar(page, "laura.ventas@ferreteria.com");
    await page.goto("/ventas/nueva");
    await elegir(page, /^cliente/i, "Juan", /Juan Pérez/i);
    await escanear(page, /^producto/i, TORNILLO);
    await escanear(page, /^producto/i, TORNILLO);   // segundo escaneo suma una unidad
    await expect(page.getByLabel("Cantidad 1")).toHaveValue("2");
    await page.getByRole("button", { name: /registrar venta/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: /registrar venta/i }).click();
    await expect(page.getByRole("heading", { name: /^venta /i })).toBeVisible();
    await expect(page.getByText(/no constituye factura electrónica/i)).toBeVisible();

    await page.getByRole("link", { name: /ver comprobante/i }).click();
    await expect(page.getByTestId("leyenda-comprobante")).toContainText(/comprobante interno/i);
    const descarga = page.waitForEvent("download");
    await page.getByRole("button", { name: /descargar pdf/i }).click();
    expect((await descarga).suggestedFilename()).toMatch(/^comprobante-.*\.pdf$/);

    await page.getByRole("link", { name: /volver al detalle/i }).click();
    await page.getByRole("link", { name: /registrar devolución/i }).click();
    await page.getByLabel(/cantidad a devolver/i).first().fill("1");
    await page.getByLabel(/^motivo/i).fill("Cliente compró de más");
    await page.getByRole("button", { name: /registrar devolución/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: /registrar devolución/i }).click();
    await expect(page.getByRole("heading", { name: /^devolución /i })).toBeVisible();
  });

  test("E2E-FLU-05 no se puede devolver más de lo vendido", async ({ page }) => {
    await entrar(page, "laura.ventas@ferreteria.com");
    await page.goto("/ventas");
    await page.getByRole("row").nth(1).getByRole("link").click();
    const boton = page.getByRole("link", { name: /registrar devolución/i });
    test.skip(await boton.count() === 0, "La última venta ya no tiene unidades por devolver");
    await boton.click();
    await page.getByLabel(/cantidad a devolver/i).first().fill("999");
    await page.getByLabel(/^motivo/i).fill("Prueba de límite");
    await page.getByRole("button", { name: /registrar devolución/i }).click();
    await expect(page.getByText(/^Máximo /).first()).toBeVisible();
    await expect(page.getByRole("dialog")).toHaveCount(0);
  });
});

test.describe("Monedas y tasas de cambio (RF-23)", () => {
  test.skip(({ isMobile }) => isMobile, "El flujo de datos se valida en escritorio");
  test("E2E-MON-01 Gerencia registra la tasa USD y Ventas factura en USD con equivalente en COP", async ({ page }) => {
    await entrar(page, "gerencia@ferreteria.com");
    await page.goto("/monedas");
    const fila = page.getByRole("row", { name: /USD · Dólar/ });
    await fila.getByRole("button", { name: /registrar tasa/i }).click();
    const dialogo = page.getByRole("dialog");
    await dialogo.getByLabel(/COP por 1 USD/i).fill("4000");
    await dialogo.getByLabel(/fuente/i).fill("Prueba E2E");
    await dialogo.getByRole("button", { name: /registrar tasa/i }).click();
    await expect(page.getByText(/tasa de cambio registrada/i)).toBeVisible();
    await salir(page);

    await entrar(page, "laura.ventas@ferreteria.com");
    await page.goto("/ventas/nueva");
    await page.getByRole("combobox", { name: /moneda del documento/i }).click();
    await page.getByRole("option", { name: /USD/ }).click();
    await elegir(page, /^cliente/i, "Juan", /Juan Pérez/i);
    await escanear(page, /^producto/i, "7701234500015");   // taladro: 650.000 COP = 162,50 USD
    await expect(page.getByText(/equivalente en COP/i)).toBeVisible();
    await page.getByRole("button", { name: /registrar venta/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: /registrar venta/i }).click();
    await expect(page.getByRole("heading", { name: /^venta /i })).toBeVisible();
    await expect(page.getByText(/1 USD = 4\.000,00 COP/).first()).toBeVisible();
    await page.getByRole("link", { name: /ver comprobante/i }).click();
    await expect(page.getByTestId("comprobante-total")).toContainText("162,50");
    await expect(page.getByTestId("comprobante-equivalente")).toContainText("650.000");
  });
});

test.describe("Permisos y navegación por rol (RF-09)", () => {
  test("E2E-ROL-02 Ventas no ve administración y una URL directa muestra acceso denegado", async ({ page, isMobile }) => {
    await entrar(page, "laura.ventas@ferreteria.com");
    if (isMobile) await page.getByRole("button", { name: /menú de navegación/i }).click();
    const menu = page.getByRole("navigation", { name: /menú de navegación/i });
    await expect(menu.getByRole("link", { name: "Ventas" })).toBeVisible();
    await expect(menu.getByRole("link", { name: "Usuarios" })).toHaveCount(0);
    await expect(menu.getByRole("link", { name: "Cierres" })).toHaveCount(0);
    await page.goto("/usuarios");
    await expect(page.getByRole("alert")).toContainText(/no tiene acceso/i);
  });

  test("E2E-ROL-03 Gerencia consulta auditoría y cierres pero no registra ventas", async ({ page }) => {
    await entrar(page, "gerencia@ferreteria.com");
    await page.goto("/auditoria");
    await expect(page.getByRole("heading", { name: "Auditoría" })).toBeVisible();
    await expect(page.getByRole("table", { name: "Eventos" })).toBeVisible();
    await page.goto("/cierres");
    await expect(page.getByRole("heading", { name: /cierres operativos/i })).toBeVisible();
    await page.goto("/ventas/nueva");
    await expect(page.getByRole("alert")).toContainText(/no tiene acceso/i);
  });
});

test.describe("Consulta sin conexión (RF-12, RN-10)", () => {
  test("E2E-OFF-01 sin conexión se consulta la copia local del catálogo y no se permiten operaciones", async ({ page, context }) => {
    await entrar(page, "marta.inventario@ferreteria.com");
    await page.getByRole("button", { name: /sincronizar para uso sin conexión/i }).click();
    await expect(page.getByText(/catálogo local actualizado/i)).toBeVisible();
    await context.setOffline(true);
    try {
      await expect(page.getByText(/copia local sincronizada/i)).toBeVisible();
      await page.getByRole("searchbox", { name: /buscar/i }).fill("taladro");
      await expect(page.getByRole("cell", { name: "Taladro", exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: /nuevo producto/i })).toBeDisabled();
    } finally {
      await context.setOffline(false);
    }
  });
});
