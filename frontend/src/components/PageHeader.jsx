/*
 * Nombre: PageHeader.jsx
 * Descripción: Encabezado de página reutilizable: título (h1), descripción y acciones alineadas, adaptable a móvil.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Box, Stack, Typography } from '@mui/material';

export default function PageHeader({ id, titulo, descripcion, acciones, volver }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'flex-start' }} gap={2} mb={3}>
      <Box>
        {volver}
        <Typography id={id} variant="h4" component="h1" sx={{ fontWeight: 600 }}>{titulo}</Typography>
        {descripcion && <Typography color="text.secondary" sx={{ mt: 0.5, maxWidth: 760 }}>{descripcion}</Typography>}
      </Box>
      {acciones && <Stack direction="row" gap={1} flexWrap="wrap" justifyContent={{ sm: 'flex-end' }}>{acciones}</Stack>}
    </Stack>
  );
}
