/*
 * Nombre: TablaEstado.jsx
 * Descripción: Fila de tabla para los estados de carga, error (con reintento) y ausencia de resultados.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Button, CircularProgress, Stack, TableCell, TableRow, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { mensajeDeError } from '../utils/mensajes';

/** Devuelve una fila si hay carga, error o lista vacía; null si hay datos que mostrar. */
export default function TablaEstado({ columnas, cargando, error, vacio, onReintentar, textoVacio }) {
  const { t } = useTranslation();
  if (cargando) {
    return <TableRow><TableCell colSpan={columnas} align="center" sx={{ py: 4 }}>
      <CircularProgress size={28} aria-label={t('comun.cargando')} /></TableCell></TableRow>;
  }
  if (error) {
    return <TableRow><TableCell colSpan={columnas} align="center" sx={{ py: 4 }}>
      <Stack alignItems="center" gap={1} role="alert">
        <Typography color="error">{mensajeDeError(t, error)}</Typography>
        {onReintentar && <Button onClick={onReintentar} size="small">{t('comun.reintentar')}</Button>}
      </Stack></TableCell></TableRow>;
  }
  if (vacio) {
    return <TableRow><TableCell colSpan={columnas} align="center" sx={{ py: 4 }}>
      <Typography color="text.secondary">{textoVacio || t('comun.sinResultados')}</Typography></TableCell></TableRow>;
  }
  return null;
}
