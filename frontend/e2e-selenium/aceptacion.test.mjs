/*
 * Nombre: aceptacion.test.mjs
 * Descripción: Pruebas de aceptación con Selenium WebDriver (Chrome headless): login, permisos y alta de producto. Ejecutar con node --test.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { Builder, By, until } from 'selenium-webdriver';
import chrome from 'selenium-webdriver/chrome.js';

const BASE = process.env.E2E_BASE_URL || 'http://localhost:5173';
const CLAVE = process.env.E2E_PASSWORD || 'Ferreteria2026*';
let driver;

before(async () => {
  driver = await new Builder().forBrowser('chrome')
    .setChromeOptions(new chrome.Options().addArguments('--headless=new', '--no-sandbox', '--window-size=1280,900')).build();
  await driver.manage().setTimeouts({ implicit: 3000 });
});
after(async () => { await driver?.quit(); });

async function entrar(correo, clave = CLAVE) {
  await driver.get(`${BASE}/login`);
  await driver.executeScript('window.localStorage.clear(); window.sessionStorage.clear();');
  await driver.get(`${BASE}/login`);
  await driver.findElement(By.id('correo')).sendKeys(correo);
  await driver.findElement(By.id('password')).sendKeys(clave);
  await driver.findElement(By.css('button[type=submit]')).click();
}


test('SEL-AUT-01 login correcto muestra el catálogo de productos', async () => {
  await entrar('marta.inventario@ferreteria.com');

  await driver.wait(async () => {
    try {
      const encabezados = await driver.findElements(By.css('h1'));

      for (const encabezado of encabezados) {
        if (
          await encabezado.isDisplayed() &&
          (await encabezado.getText()).trim() === 'Productos'
        ) {
          return true;
        }
      }

      return false;
    } catch (error) {
      if (error.name === 'StaleElementReferenceError') {
        return false;
      }

      throw error;
    }
  }, 10000, 'No apareció el encabezado Productos después del login');
});

test('SEL-AUT-02 credenciales inválidas muestran alerta con role=alert', async () => {
  await entrar('laura.ventas@ferreteria.com', 'incorrecta');
  const alerta = await driver.wait(until.elementLocated(By.css('[role=alert]')), 8000);
  assert.match(await alerta.getText(), /credenciales/i);
});

test('SEL-ROL-01 el rol Ventas no ve el botón "Nuevo producto"', async () => {
  await entrar('laura.ventas@ferreteria.com');
  await driver.wait(until.elementLocated(By.css('h1')), 8000);
  const botones = await driver.findElements(By.xpath("//button[contains(., 'Nuevo producto')]"));
  assert.equal(botones.length, 0);
});

test('SEL-PRO-01 un código de barras inválido bloquea el guardado y muestra el error', async () => {
  await entrar('marta.inventario@ferreteria.com');
  await driver.wait(until.elementLocated(By.xpath("//button[contains(., 'Nuevo producto')]")), 8000).click();
  await driver.findElement(By.id('campo-codigoBarras')).sendKeys('123');
  await driver.findElement(By.xpath("//button[normalize-space()='Guardar']")).click();
  const msg = await driver.wait(until.elementLocated(By.xpath("//*[contains(text(), 'Entre 8 y 14 dígitos')]")), 5000);
  assert.ok(await msg.isDisplayed());
});

test('SEL-SEG-01 sin sesión, /ventas redirige a /login', async () => {
  await driver.get(`${BASE}/login`);
  await driver.executeScript('window.localStorage.clear(); window.sessionStorage.clear();');
  await driver.get(`${BASE}/ventas`);
  await driver.wait(until.urlContains('/login'), 5000);
});
