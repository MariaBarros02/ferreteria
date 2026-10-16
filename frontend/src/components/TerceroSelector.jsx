/*
 * Nombre: TerceroSelector.jsx
 * Descripción: Selector de clientes o proveedores con búsqueda por nombre, identificación o correo; muestra datos de contacto antes de seleccionar.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useCallback } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../api/http';
import EntitySelector from './EntitySelector';

export const etiquetaTercero = (x) => `${x.nombre} · ${x.tipoIdentificacion} ${x.numeroIdentificacion}`;

export default function TerceroSelector({ id, rol = 'cliente', valor, onCambio, label, ...resto }) {
  const { t } = useTranslation();
  const buscar = useCallback((q) => api.terceros.seleccion(q, rol, 15), [rol]);
  return (
    <EntitySelector id={id || `selector-${rol}`} label={label || t(`selector.${rol}`)} valor={valor} onCambio={onCambio}
      buscar={buscar} etiqueta={etiquetaTercero} placeholder={t('selector.terceroAyuda')}
      renderOpcion={(props, x) => (
        <Box component="li" {...props}>
          <Stack>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{x.nombre}</Typography>
            <Typography variant="caption" color="text.secondary">
              {x.tipoIdentificacion} {x.numeroIdentificacion} · {x.ciudad} · {x.telefono}
            </Typography>
          </Stack>
        </Box>
      )}
      {...resto} />
  );
}
