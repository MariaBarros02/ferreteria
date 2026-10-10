/*
 * Nombre: venta-concurrente.js
 * Descripción: Concurrencia con k6 (RN-11): 100 usuarios venden el mismo producto con stock S; deben ocurrir exactamente S éxitos y el resto 409.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
// Preparar: producto con stock = STOCK y precio vigente aprobado; pasar PRODUCTO_ID y un CLIENTE_ID (tercero cliente).
// Uso: k6 run -e PRODUCTO_ID=1 -e CLIENTE_ID=1 -e STOCK=50 -e USER=... -e PASS=... tests/stress/venta-concurrente.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const STOCK = Number(__ENV.STOCK || 50);
const exitos = new Counter('ventas_exitosas');
const rechazos = new Counter('ventas_rechazadas_409');
const inesperados = new Counter('respuestas_inesperadas');

export const options = {
  scenarios: { rafaga: { executor: 'shared-iterations', vus: 100, iterations: STOCK * 3, maxDuration: '2m' } },
  thresholds: { ventas_exitosas: [`count==${STOCK}`], respuestas_inesperadas: ['count==0'] },
};

export function setup() {
  const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ correo: __ENV.USER, password: __ENV.PASS }), { headers: { 'Content-Type': 'application/json' } });
  return { token: r.json('token') };
}

export default function (d) {
  const cuerpo = JSON.stringify({ clienteId: Number(__ENV.CLIENTE_ID), lineas: [{ productoId: Number(__ENV.PRODUCTO_ID), cantidad: 1 }] });
  const r = http.post(`${BASE}/api/ventas`, cuerpo, { headers: { Authorization: `Bearer ${d.token}`, 'Content-Type': 'application/json' } });
  if (r.status === 201) exitos.add(1); else if (r.status === 409) rechazos.add(1); else inesperados.add(1);
  check(r, { '201 o 409 (nunca 5xx)': (x) => x.status === 201 || x.status === 409 });
}

export function teardown(d) {
  const r = http.get(`${BASE}/api/productos/${__ENV.PRODUCTO_ID}/existencias`, { headers: { Authorization: `Bearer ${d.token}` } });
  check(r, { 'saldo final = 0 (nunca negativo)': (x) => Number(x.json('disponible')) === 0 });
}
