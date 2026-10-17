/*
 * Nombre: catalogo.js
 * Descripción: Copia local del catálogo para consulta sin conexión (RF-12): sincronización paginada en IndexedDB, búsqueda local y borrado al cerrar sesión. Solo lectura: no existen operaciones sin conexión (RN-10).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
const BD = 'ferreteria-offline';
const ALMACEN = 'catalogo';
const META = 'meta';
let memoria = { productos: [], sincronizado: null };   // respaldo si IndexedDB no está disponible

function abrir() {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') { reject(new Error('sin-indexeddb')); return; }
    const req = indexedDB.open(BD, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(ALMACEN)) db.createObjectStore(ALMACEN, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(META)) db.createObjectStore(META);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function escribir(productos, sincronizado) {
  try {
    const db = await abrir();
    await new Promise((resolve, reject) => {
      const tx = db.transaction([ALMACEN, META], 'readwrite');
      const st = tx.objectStore(ALMACEN);
      st.clear();
      productos.forEach((p) => st.put(p));
      tx.objectStore(META).put(sincronizado, 'sincronizado');
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  } catch {
    memoria = { productos, sincronizado };
  }
}

/** Lee la copia local: { productos, sincronizado } (sincronizado = ISO o null). */
export async function leerCatalogo() {
  try {
    const db = await abrir();
    const r = await new Promise((resolve, reject) => {
      const tx = db.transaction([ALMACEN, META], 'readonly');
      const a = tx.objectStore(ALMACEN).getAll();
      const m = tx.objectStore(META).get('sincronizado');
      tx.oncomplete = () => resolve({ productos: a.result || [], sincronizado: m.result || null });
      tx.onerror = () => reject(tx.error);
    });
    db.close();
    return r;
  } catch {
    return memoria;
  }
}

/**
 * Descarga el catálogo por páginas y reemplaza la copia local.
 * @param {(pagina:number)=>Promise<{contenido:object[], totalPaginas:number}>} obtenerPagina función de la API
 * @returns {Promise<{total:number, sincronizado:string}>}
 */
export async function sincronizarCatalogo(obtenerPagina, ahora = () => new Date()) {
  const todos = [];
  for (let pagina = 0; pagina < 500; pagina += 1) {
    const r = await obtenerPagina(pagina);
    todos.push(...(r.contenido || []));
    if (pagina + 1 >= (r.totalPaginas || 0)) break;
  }
  const sincronizado = ahora().toISOString();
  await escribir(todos, sincronizado);
  return { total: todos.length, sincronizado };
}

/** Normaliza texto para buscar sin tildes ni mayúsculas. */
const norm = (s) => String(s ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

/** Busca en la copia local por nombre, marca, presentación o código. */
export function buscarLocal(productos, q) {
  const t = norm(q).trim();
  if (!t) return productos;
  return productos.filter((p) => [p.nombre, p.marca, p.presentacion, p.codigoBarras].some((x) => norm(x).includes(t)));
}

/** Elimina la copia local (al cerrar sesión: los datos pertenecen al usuario autenticado). */
export async function borrarCatalogo() {
  memoria = { productos: [], sincronizado: null };
  try {
    const db = await abrir();
    await new Promise((resolve) => {
      const tx = db.transaction([ALMACEN, META], 'readwrite');
      tx.objectStore(ALMACEN).clear();
      tx.objectStore(META).clear();
      tx.oncomplete = resolve;
      tx.onerror = resolve;
    });
    db.close();
  } catch { /* sin IndexedDB */ }
}
