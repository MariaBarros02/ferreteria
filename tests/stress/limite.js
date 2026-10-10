/*
 * Nombre: limite.js
 * Descripción: Pruebas de límite con k6: payloads extremos, paginación fuera de rango, fuerza bruta de login y pico (spike).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
// Uso: k6 run -e BASE=... -e USER=... -e PASS=... tests/stress/limite.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
export const options = {
  scenarios: {
    limites: { executor: 'per-vu-iterations', vus: 1, iterations: 1, exec: 'limites' },
    spike: { executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 400, maxVUs: 600, startTime: '10s', exec: 'spike',
      stages: [{ target: 10, duration: '10s' }, { target: 400, duration: '10s' }, { target: 10, duration: '20s' }] },
  },
  thresholds: { checks: ['rate>0.99'], 'http_req_failed{scenario:spike}': ['rate<0.05'] },
};

export function setup() {
  const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ correo: __ENV.USER, password: __ENV.PASS }), { headers: { 'Content-Type': 'application/json' } });
  return { token: r.json('token') };
}
const J = (t) => ({ headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } });
const LOGIN = { headers: { 'Content-Type': 'application/json' } };

export function limites(d) {
  check(http.get(`${BASE}/api/productos?tamano=101`, J(d.token)), { 'tamaño > 100 rechazado (400)': (r) => r.status === 400 });
  check(http.get(`${BASE}/api/productos?pagina=-1`, J(d.token)), { 'página negativa rechazada (400)': (r) => r.status === 400 });
  check(http.get(`${BASE}/api/productos?pagina=99999999`, J(d.token)), { 'página enorme devuelve vacío (200)': (r) => r.status === 200 && r.json('contenido').length === 0 });
  check(http.get(`${BASE}/api/productos?q=${'a'.repeat(5000)}`, J(d.token)), { 'filtro de 5000 caracteres rechazado': (r) => r.status === 400 || r.status === 414 });
  const gigante = JSON.stringify({ nombre: 'x'.repeat(2000000), marca: 'm', presentacion: 'p', codigoBarras: '12345678', unidadBase: 'u', tipoRastreo: 'NINGUNO' });
  check(http.post(`${BASE}/api/productos`, gigante, J(d.token)), { 'cuerpo gigante no provoca 5xx': (r) => r.status < 500 });
  const lineas = Array.from({ length: 201 }, () => ({ productoId: 1, cantidad: 1 }));
  check(http.post(`${BASE}/api/ventas`, JSON.stringify({ clienteId: 1, lineas }), J(d.token)), { '201 líneas rechazadas (límite 200)': (r) => r.status === 400 });
  check(http.post(`${BASE}/api/ventas`, JSON.stringify({ clienteId: 1, lineas: [{ productoId: 1, cantidad: 99999999999 }] }), J(d.token)), { 'cantidad desbordada rechazada': (r) => r.status === 400 });
  for (let i = 0; i < 5; i++) http.post(`${BASE}/api/auth/login`, JSON.stringify({ correo: 'victima@x.com', password: 'mal' }), LOGIN);
  check(http.post(`${BASE}/api/auth/login`, JSON.stringify({ correo: 'victima@x.com', password: 'mal' }), LOGIN), { 'fuerza bruta: tras 5 fallos responde 429': (r) => r.status === 429 });
}

export function spike(d) {
  check(http.get(`${BASE}/api/productos?tamano=20`, J(d.token)), { 'consulta durante el pico responde 200': (r) => r.status === 200 });
}
