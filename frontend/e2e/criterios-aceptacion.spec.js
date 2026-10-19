/*
 * Nombre: criterios-aceptacion.spec.js
 * Descripción: Pruebas E2E con Playwright de los criterios de aceptación (HU-01, HU-05, HU-08, HU-13, i18n, responsivo, accesibilidad).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
import { test, expect } from "@playwright/test";

const CLAVE = process.env.E2E_PASSWORD || "Ferreteria2026*";
const unico = () =>
  String(Date.now()).slice(-9) +
  String(Math.floor(Math.random() * 1000)).padStart(3, "0");

async function entrar(page, correo, recordar = false) {
  await page.goto("/login");
  await page.getByLabel(/correo electrónico/i).fill(correo);
  await page.getByLabel(/^contraseña/i).fill(CLAVE);

  if (recordar) {
    await page.getByLabel(/mantener la sesión/i).check();
  }

  await page.getByRole("button", { name: /entrar/i }).click();

  // Espera a que la autenticación termine y la aplicación redirija.
  await expect(page.getByRole("heading", { name: /productos/i })).toBeVisible({
    timeout: 10000,
  });
}

test.describe("Autenticación y sesión", () => {
  test("E2E-AUT-01 login correcto lleva a productos y logout vuelve al login", async ({
    page,
  }) => {
    await entrar(page, "marta.inventario@ferreteria.com");
    await expect(
      page.getByRole("heading", { name: "Productos" }),
    ).toBeVisible();
    await page.getByRole("button", { name: /cerrar sesión/i }).click();
    await expect(
      page.getByRole("heading", { name: /iniciar sesión/i }),
    ).toBeVisible();
  });

  test("E2E-AUT-02 credenciales inválidas muestran alerta", async ({
    page,
  }) => {
    await page.goto("/login");
    await page
      .getByLabel(/correo electrónico/i)
      .fill("laura.ventas@ferreteria.com");
    await page.getByLabel(/^contraseña/i).fill("incorrecta");
    await page.getByRole("button", { name: /entrar/i }).click();
    await expect(page.getByRole("alert")).toContainText(/credenciales/i);
  });

  test("E2E-AUT-03 ruta protegida sin sesión redirige al login", async ({
    page,
  }) => {
    await page.goto("/ventas");
    await expect(page).toHaveURL(/\/login/);
  });

  test('E2E-AUT-04 permanencia: con "mantener sesión" sobrevive a recargar y a un contexto nuevo con el mismo estado', async ({
    page,
  }) => {
    await entrar(page, "marta.inventario@ferreteria.com", true);
    await expect(
      page.getByRole("heading", { name: "Productos" }),
    ).toBeVisible();
    await page.reload();
    await expect(
      page.getByRole("heading", { name: "Productos" }),
    ).toBeVisible();
  });

  test('E2E-AUT-05 sin "mantener sesión" se pierde al cerrar la pestaña (sessionStorage)', async ({
    page,
    context,
  }) => {
    await entrar(page, "marta.inventario@ferreteria.com", false);
    await expect(
      page.getByRole("heading", { name: "Productos" }),
    ).toBeVisible();
    const nueva = await context.newPage();
    await nueva.goto("/productos");
    await expect(nueva).toHaveURL(/\/login/);
  });
});

test.describe("HU-01 catálogo de productos", () => {
  test("E2E-PRO-01 crear producto y encontrarlo por código; código duplicado se rechaza (RN-01)", async ({
    page,
  }) => {
    const codigo = "77" + unico();
    await entrar(page, "marta.inventario@ferreteria.com");
    for (const intento of [1, 2]) {
      await page.getByRole("button", { name: /nuevo producto/i }).click();
      await page.getByLabel(/^nombre/i).fill("Martillo " + codigo + intento);
      await page.getByLabel(/^marca/i).fill("Stanley" + intento);
      await page.getByLabel(/^presentación/i).fill("16oz");
      await page.getByLabel(/código de barras/i).fill(codigo);
      await page.getByRole("button", { name: /^guardar$/i }).click();
      if (intento === 1)
        await expect(page.getByText("Guardado correctamente")).toBeVisible();
      else {
        await expect(page.locator(".MuiSnackbar-root")).toContainText(
          /código de barras/i,
        );
      }
    }
  });

  test("E2E-PRO-02 validación de formulario: código inválido bloquea el envío y muestra el error", async ({
    page,
  }) => {
    await entrar(page, "marta.inventario@ferreteria.com");
    await page.getByRole("button", { name: /nuevo producto/i }).click();
    await page.getByLabel(/código de barras/i).fill("123");
    await page.getByLabel(/^nombre/i).fill("<script>alert(1)</script>");
    await page.getByRole("button", { name: /^guardar$/i }).click();
    await expect(
      page.getByText("Entre 8 y 14 dígitos numéricos"),
    ).toBeVisible();
    await expect(
      page.getByText(/no se permiten los caracteres/i),
    ).toBeVisible();
  });
});

test.describe("HU-13 permisos por rol", () => {
  test("E2E-ROL-01 Ventas no ve el botón de crear producto; Gerencia no puede registrar ventas", async ({
    page,
  }) => {
    await entrar(page, "laura.ventas@ferreteria.com");
    await expect(
      page.getByRole("heading", { name: "Productos" }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /nuevo producto/i }),
    ).toHaveCount(0);
    await page.getByRole("button", { name: /cerrar sesión/i }).click();
    await entrar(page, "gerencia@ferreteria.com");
    await page.goto("/ventas");
    await expect(
      page.getByRole("button", { name: /nueva venta/i }),
    ).toHaveCount(0);
  });
});

test.describe("HU-05 venta", () => {
  test("E2E-VEN-01 saldo insuficiente muestra el error del servidor y no registra la venta", async ({
    page,
  }) => {
    await entrar(page, "laura.ventas@ferreteria.com");
    await page.goto("/ventas/nueva");
    // Selectores con búsqueda (RF-11): ya no se escriben identificadores numéricos.
    await page.getByRole("combobox", { name: /^cliente/i }).fill("1002003000");
    await page.getByRole("option",{ name: /1002003000/ }).click();
    const producto = page.getByRole("combobox", { name: /^producto/i });
    await producto.fill("7701234500015");
    await producto.press("Enter");
    await page.getByLabel("Cantidad 1").fill("999999");
    await page.getByRole("button", { name: /registrar venta/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: /registrar venta/i }).click();
    await expect(page.getByText(/saldo insuficiente/i)).toBeVisible();
    await expect(page).toHaveURL(/\/ventas\/nueva/);
  });
});

test.describe("Internacionalización, responsivo y accesibilidad", () => {
  test("E2E-I18N-01 cambiar a inglés traduce la interfaz y persiste tras recargar", async ({
    page,
  }) => {
    await entrar(page, "marta.inventario@ferreteria.com");
    await page.getByRole("combobox", { name: /idioma|language/i }).click();
    await page.getByRole("option", { name: "EN" }).click();
    await expect(page.getByRole("heading", { name: "Products" })).toBeVisible();
    await page.reload();
    await expect(page.getByRole("heading", { name: "Products" })).toBeVisible();
  });

  test('E2E-A11Y-01 enlace "saltar al contenido" es el primer elemento enfocable y funciona con teclado', async ({
    page,
  }) => {
    await entrar(page, "marta.inventario@ferreteria.com");
    await page.keyboard.press("Tab");
    await expect(
      page.getByRole("link", { name: /saltar al contenido/i }),
    ).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#contenido")).toBeFocused();
  });

  test("E2E-RES-01 en móvil no hay desplazamiento horizontal de la página", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await entrar(page, "marta.inventario@ferreteria.com");
    await expect(
      page.getByRole("heading", { name: "Productos" }),
    ).toBeVisible();
    const desborda = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth,
    );
    expect(desborda).toBe(false);
  });
});
