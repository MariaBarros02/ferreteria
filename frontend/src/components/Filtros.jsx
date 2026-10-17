/*
 * Nombre: Filtros.jsx
 * Descripción: Contenedor de filtros de listados y campo de fecha accesible (etiqueta siempre visible).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Paper, Stack, TextField } from '@mui/material';

export function Filtros({ children, etiqueta }) {
  return (
    <Paper variant="outlined" component="form" role="search" aria-label={etiqueta} onSubmit={(e) => e.preventDefault()}
      sx={{ p: 2, mb: 2 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} gap={2} alignItems={{ md: 'center' }} flexWrap="wrap">{children}</Stack>
    </Paper>
  );
}

export function CampoFecha({ label, value, onChange, ...resto }) {
  return <TextField type="date" size="small" label={label} value={value} onChange={(e) => onChange(e.target.value)}
    InputLabelProps={{ shrink: true }} sx={{ minWidth: 170 }} {...resto} />;
}
