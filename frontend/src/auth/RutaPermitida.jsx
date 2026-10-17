/*
 * Nombre: RutaPermitida.jsx
 * Descripción: Guarda de ruta por permiso: si el rol no tiene la acción, muestra un aviso de acceso denegado en lugar de la página (la API sigue siendo la autoridad final).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Alert, Button, Stack } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from './AuthContext';

export default function RutaPermitida({ permitido, children }) {
  const { t } = useTranslation();
  const { rol } = useAuth();
  if (permitido(rol)) return children;
  return (
    <Stack gap={2} alignItems="flex-start">
      <Alert severity="warning" role="alert">{t('comun.accesoDenegado')}</Alert>
      <Button component={RouterLink} to="/productos">{t('comun.irInicio')}</Button>
    </Stack>
  );
}
