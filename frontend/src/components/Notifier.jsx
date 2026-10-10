/*
 * Nombre: Notifier.jsx
 * Descripción: Notificaciones (Snackbar + Alert con role="alert"/aria-live) disponibles mediante el hook useNotify.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { Alert, Snackbar } from '@mui/material';

const NotifyContext = createContext(() => {});

/**
 * Proveedor de notificaciones.
 * @param {{children: import('react').ReactNode}} props - Hijos.
 * @returns {JSX.Element} Proveedor.
 */
export function NotifierProvider({ children }) {
  const [msg, setMsg] = useState(null);
  const notificar = useCallback((texto, severidad = 'success') => setMsg({ texto, severidad, id: Date.now() }), []);
  const valor = useMemo(() => notificar, [notificar]);
  return (
    <NotifyContext.Provider value={valor}>
      {children}
      <Snackbar key={msg?.id} open={Boolean(msg)} autoHideDuration={6000} onClose={() => setMsg(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
        {msg ? <Alert severity={msg.severidad} variant="filled" onClose={() => setMsg(null)} role="alert">{msg.texto}</Alert> : undefined}
      </Snackbar>
    </NotifyContext.Provider>
  );
}

/** @returns {(texto:string, severidad?:'success'|'error'|'info'|'warning')=>void} Función para notificar. */
export const useNotify = () => useContext(NotifyContext);
