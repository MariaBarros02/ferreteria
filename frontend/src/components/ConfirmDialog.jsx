/*
 * Nombre: ConfirmDialog.jsx
 * Descripción: Diálogo de confirmación accesible (foco atrapado, aria-labelledby/describedby) para acciones sensibles; texto y color del botón configurables.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
import { Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { useTranslation } from 'react-i18next';

export default function ConfirmDialog({ abierto, titulo, mensaje, onConfirmar, onCancelar, textoConfirmar, color = 'error',
  cargando = false, children }) {
  const { t } = useTranslation();
  return (
    <Dialog open={abierto} onClose={cargando ? undefined : onCancelar} aria-labelledby="confirmar-titulo" aria-describedby="confirmar-mensaje">
      <DialogTitle id="confirmar-titulo">{titulo}</DialogTitle>
      <DialogContent>
        <DialogContentText id="confirmar-mensaje" component="div">{mensaje}</DialogContentText>
        {children}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancelar} disabled={cargando}>{t('comun.cancelar')}</Button>
        <Button onClick={onConfirmar} color={color} variant="contained" autoFocus disabled={cargando}
          startIcon={cargando ? <CircularProgress size={16} color="inherit" /> : null}>
          {textoConfirmar || t('comun.eliminar')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
