/*
 * Nombre: ProductoSelector.jsx
 * Descripción: Selector de productos activos con búsqueda por nombre, marca, presentación o código de barras; muestra existencias, precio vigente y alertas, y acepta lectores de código (Enter) (RF-11).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useCallback } from 'react';
import { Box, Chip, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../api/http';
import EntitySelector from './EntitySelector';
import { useNotify } from './Notifier';
import { cantidad, moneda } from '../utils/formato';
import { mensajeDeError } from '../utils/mensajes';

const CODIGO = /^\d{8,14}$/;

export const etiquetaProducto = (p) => `${p.nombre} ${p.marca} ${p.presentacion}`;

export default function ProductoSelector({ id = 'selector-producto', valor, onCambio, label, mostrarPrecio = true, ...resto }) {
  const { t } = useTranslation();
  const notificar = useNotify();
  const buscar = useCallback((q) => api.productos.seleccion(q, 15), []);
  const porCodigo = useCallback(async (texto) => {
    if (!CODIGO.test(texto)) return false;
    try {
      onCambio(await api.productos.porCodigo(texto));
    } catch (e) {
      notificar(e?.response?.status === 404 ? t('selector.codigoNoEncontrado', { codigo: texto }) : mensajeDeError(t, e), 'warning');
    }
    return true;
  }, [onCambio, notificar, t]);
  return (
    <EntitySelector id={id} label={label || t('selector.producto')} valor={valor} onCambio={onCambio} buscar={buscar}
      etiqueta={etiquetaProducto} onEnter={porCodigo} placeholder={t('selector.productoAyuda')}
      renderOpcion={(props, p) => (
        <Box component="li" {...props}>
          <Stack sx={{ width: '100%' }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{etiquetaProducto(p)}</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
              <Typography variant="caption" color="text.secondary">{p.codigoBarras}</Typography>
              <Typography variant="caption">{t('selector.disponible', { cantidad: cantidad(p.disponible), unidad: p.unidadBase })}</Typography>
              {mostrarPrecio && <Typography variant="caption">{p.precioVigente != null ? moneda(p.precioVigente) : t('selector.sinPrecio')}</Typography>}
              {Number(p.disponible) === 0 && <Chip size="small" color="error" label={t('estados.AGOTADO')} />}
              {Number(p.vencidoSinBaja) > 0 && <Chip size="small" color="warning" label={t('selector.conVencidos')} />}
            </Stack>
          </Stack>
        </Box>
      )}
      {...resto} />
  );
}
