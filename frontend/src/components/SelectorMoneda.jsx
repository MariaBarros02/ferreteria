/*
 * Nombre: SelectorMoneda.jsx
 * Descripción: Selector de moneda del documento (RF-23): monedas activas con su tasa vigente; las que no tienen tasa aparecen deshabilitadas con la explicación. Devuelve la moneda completa (código, decimales y tasa).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect } from 'react';
import { ListItemText, MenuItem, TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../api/http';
import useCarga from '../hooks/useCarga';
import { fecha, tasaTexto } from '../utils/formato';

/** Moneda funcional de respaldo mientras carga la lista (o si falla). */
export const COP = { codigo: 'COP', nombre: 'Peso colombiano', simbolo: '$', decimales: 0, activa: true, funcional: true, tasaVigente: 1 };

export default function SelectorMoneda({ id = 'selector-moneda', valor, onCambio, deshabilitado, label }) {
  const { t } = useTranslation();
  const { datos } = useCarga(() => api.monedas.listar(true), [], []);
  const monedas = datos?.length ? datos : [COP];
  const actual = valor?.codigo || 'COP';

  // Si la moneda elegida cambia de tasa al recargar, se actualiza el objeto completo.
  useEffect(() => {
    const m = monedas.find((x) => x.codigo === actual);
    if (m && valor && m.tasaVigente !== valor.tasaVigente) onCambio(m);
  }, [datos]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <TextField id={id} select size="small" label={label || t('moneda.monedaDocumento')} value={actual} disabled={deshabilitado}
      onChange={(e) => onCambio(monedas.find((m) => m.codigo === e.target.value) || COP)} sx={{ minWidth: 220 }}
      SelectProps={{ renderValue: (v) => { const m = monedas.find((x) => x.codigo === v); return m ? `${m.codigo} · ${m.nombre}` : v; } }}
      helperText={valor && !valor.funcional ? `${tasaTexto(valor.codigo, valor.tasaVigente)} · ${fecha(valor.fechaTasa)}` : t('moneda.funcionalAyuda')}>
      {monedas.map((m) => {
        const sinTasa = !m.funcional && (m.tasaVigente === null || m.tasaVigente === undefined);
        return (
          <MenuItem key={m.codigo} value={m.codigo} disabled={sinTasa}>
            <ListItemText primary={`${m.codigo} · ${m.nombre}`} secondary={sinTasa ? t('moneda.sinTasa') : m.funcional ? t('moneda.funcional') : tasaTexto(m.codigo, m.tasaVigente)} />
          </MenuItem>);
      })}
    </TextField>
  );
}
