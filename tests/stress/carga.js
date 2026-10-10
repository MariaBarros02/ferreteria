/*
 * Nombre: carga.js
 * Descripción: Prueba de carga/estrés con k6 sobre los escenarios de RNF-01 (consulta por código, listados) con umbrales de aceptación.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
// Uso: k6 run -e BASE=http://localhost:8080 -e USER=laura.ventas@ferreteria.com -e PASS=... tests/stress/carga.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const errores = new Rate('errores_funcionales');

export const options = {
  scenarios: {
    carga_normal: { executor: 'ramping-vus', startVUs: 0, exec: 'consultas', stages: [
      { duration: '1m', target: 25 }, { duration: '3m', target: 50 }, { duration: '1m', target: 0 }] },
    estres: { executor: 'ramping-vus', startTime: '6m', startVUs: 0, exec: 'consultas', stages: [
      { duration: '2m', target: 100 }, { duration: '2m', target: 200 }, { duration: '2m', target: 300 }, { duration: '1m', target: 0 }] },
  },
  thresholds: {
    'http_req_duration{scenario:carga_normal}': ['p(95)<500', 'p(99)<1000'], // RNF-01: 50 usuarios simultáneos
    'http_req_failed{scenario:carga_normal}': ['rate<0.01'],
    errores_funcionales: ['rate<0.01'],
  },
};

export function setup() {
  const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ correo: __ENV.USER, password: __ENV.PASS }), { headers: { 'Content-Type': 'application/json' } });
  check(r, { 'login 200': (x) => x.status === 200 });
  return { token: r.json('token') };
}

export function consultas(d) {
  const h = { headers: { Authorization: `Bearer ${d.token}` } };
  const lista = http.get(`${BASE}/api/productos?pagina=0&tamano=20`, h);
  const porCodigo = http.get(`${BASE}/api/productos/codigo/7701234500015`, h);
  errores.add(lista.status !== 200 || porCodigo.status !== 200);
  check(porCodigo, { 'código conocido devuelve siempre la misma referencia': (x) => x.json('codigoBarras') === '7701234500015' });
  sleep(Math.random());
}
