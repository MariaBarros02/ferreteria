/*
 * Nombre: asistente.spec.js
 * Descripción: Pruebas E2E con Playwright del asistente virtual: acceso desde la barra superior, pregunta frecuente con enlace a la pantalla, consulta de un producto real por código, aviso de permisos por rol y uso en móvil.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { test, expect } from "@playwright/test";

const CLAVE = process.env.E2E_PASSWORD || "Ferreteria2026*";
const TALADRO = "7701234500015"; // producto del seed (database/02_seed.sql)

async function entrar(page, correo) {
  await page.goto("/login");
  await page.getByLabel(/correo electrónico/i).fill(correo);
  await page.getByLabel(/^contraseña/i).fill(CLAVE);
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page.getByRole("heading", { name: /productos/i })).toBeVisible({ timeout: 10000 });
}

async function abrirAsistente(page) {
  await page.getByRole("button", { name: /abrir el asistente virtual/i }).click();
  const panel = page.getByRole("dialog", { name: /asistente virtual/i });
  await expect(panel.getByText(/soy el asistente virtual/i)).toBeVisible();
  return panel;
}

async function preguntar(panel, texto) {
  await panel.getByLabel(/escribe tu pregunta/i).fill(texto);
  await panel.getByRole("button", { name: /enviar pregunta/i }).click();
}

test.describe("Asistente virtual", () => {
  test("E2E-ASI-01 Ventas pregunta cómo registrar una venta y el enlace la lleva a la pantalla", async ({ page }) => {
    await entrar(page, "laura.ventas@ferreteria.com");
    const panel = await abrirAsistente(page);
    await preguntar(panel, "¿Cómo registro una venta?");
    await expect(panel.getByText(/1\. Abre Ventas → Nueva venta\./)).toBeVisible();
    await panel.getByRole("link", { name: "Abrir Nueva venta" }).click();
    await expect(page).toHaveURL(/\/ventas\/nueva$/);
    await expect(page.getByRole("dialog", { name: /asistente virtual/i })).toBeHidden();
  });

  test("E2E-ASI-02 consulta el precio y las existencias de un producto por su código", async ({ page }) => {
    await entrar(page, "laura.ventas@ferreteria.com");
    const panel = await abrirAsistente(page);
    await preguntar(panel, TALADRO);
    await expect(panel.getByText(new RegExp(`código ${TALADRO}`))).toBeVisible();
    await expect(panel.getByText(/Disponible: /)).toBeVisible();
  });

  test("E2E-ASI-03 un tema elegido muestra el aviso de permisos para Gerencia y no ofrece el enlace", async ({ page }) => {
    await entrar(page, "gerencia@ferreteria.com");
    const panel = await abrirAsistente(page);
    await panel.getByRole("button", { name: "Ventas", exact: true }).click();
    await panel.getByRole("button", { name: "¿Cómo registro una venta?" }).click();
    await expect(panel.getByText(/Tu rol no tiene permiso/)).toBeVisible();
    await expect(panel.getByRole("link", { name: "Abrir Nueva venta" })).toHaveCount(0);
  });

  test("E2E-ASI-04 en móvil el panel ocupa la pantalla sin desplazamiento horizontal y se cierra", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await entrar(page, "laura.ventas@ferreteria.com");
    const panel = await abrirAsistente(page);
    await preguntar(panel, "precio del taladro");
    await expect(panel.getByText(/Taladro/).first()).toBeVisible();
    const desborda = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(desborda).toBe(false);
    await panel.getByRole("button", { name: /cerrar el asistente/i }).click();
    await expect(page.getByRole("dialog", { name: /asistente virtual/i })).toBeHidden();
  });
});
