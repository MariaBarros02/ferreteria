/*
 * Nombre: useCarga.js
 * Descripción: Hook para cargas asíncronas con estados de carga, error y datos, y recarga manual.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Ejecuta `fn` cuando cambian las dependencias y expone { datos, cargando, error, recargar }.
 * Ignora respuestas de cargas obsoletas (condición de carrera entre filtros).
 */
export default function useCarga(fn, deps, inicial = null) {
  const [estado, setEstado] = useState({ datos: inicial, cargando: true, error: null });
  const ultimo = useRef(0);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const ejecutar = useCallback(fn, deps);
  const recargar = useCallback(async () => {
    const n = ++ultimo.current;
    setEstado((e) => ({ ...e, cargando: true, error: null }));
    try {
      const datos = await ejecutar();
      if (n === ultimo.current) setEstado({ datos, cargando: false, error: null });
    } catch (error) {
      if (n === ultimo.current) setEstado((e) => ({ ...e, cargando: false, error }));
    }
  }, [ejecutar]);
  useEffect(() => { recargar(); }, [recargar]);
  return { ...estado, recargar };
}
