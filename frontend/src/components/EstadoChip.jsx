/*
 * Nombre: EstadoChip.jsx
 * Descripción: Etiqueta de estado con color semántico y texto traducido (documentos, aprobaciones, recepciones, alertas).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Chip } from '@mui/material';
import { useTranslation } from 'react-i18next';

const COLOR = {
  ACTIVO: 'success', ANULADO: 'default', PENDIENTE: 'warning', PARCIAL: 'info', RECIBIDA: 'success', ANULADA: 'default',
  APROBADO: 'success', RECHAZADO: 'error', propuesto: 'warning', aprobado: 'success', rechazado: 'error',
  ABIERTA: 'info', CERRADA: 'warning', APROBADA: 'success', RECHAZADA: 'error', ACTIVA: 'info',
  LIBERADA_VENTA: 'success', LIBERADA_CANCELACION: 'default', LIBERADA_VENCIMIENTO: 'default',
  AGOTADO: 'error', STOCK_BAJO: 'warning', VENCIDO: 'error', POR_VENCER: 'warning', VIGENTE: 'success', SIN_FECHA: 'default',
  CERRADO: 'warning', REABIERTO: 'info', ABIERTO: 'success', VALIDA: 'success', ERROR: 'error', DUPLICADO: 'default',
  POSIBLE_DUPLICADO: 'warning', IMPORTADA: 'success', OMITIDA: 'default', VALIDADA: 'info', IMPORTADO: 'success',
  CANCELADA: 'default', INACTIVO: 'default',
};

export default function EstadoChip({ estado, size = 'small' }) {
  const { t } = useTranslation();
  if (!estado) return null;
  const clave = String(estado);
  return <Chip size={size} color={COLOR[clave] || 'default'} variant={COLOR[clave] ? 'filled' : 'outlined'}
    label={t(`estados.${clave}`, { defaultValue: clave })} />;
}
