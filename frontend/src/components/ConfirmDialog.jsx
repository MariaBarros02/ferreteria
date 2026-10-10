/*
 * Nombre: ConfirmDialog.jsx
 * Descripción: Diálogo de confirmación accesible (foco atrapado, etiquetado con aria-labelledby/describedby) para acciones destructivas.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { useTranslation } from 'react-i18next';

/**
 * Confirmación modal.
 * @param {{abierto:boolean, titulo:string, mensaje:string, onConfirmar:Function, onCancelar:Function}} props - Propiedades.
 * @returns {JSX.Element} Diálogo.
 */
export default function ConfirmDialog({ abierto, titulo, mensaje, onConfirmar, onCancelar }) {
  const { t } = useTranslation();
  return (
    <Dialog open={abierto} onClose={onCancelar} aria-labelledby="confirmar-titulo" aria-describedby="confirmar-mensaje">
      <DialogTitle id="confirmar-titulo">{titulo}</DialogTitle>
      <DialogContent><DialogContentText id="confirmar-mensaje">{mensaje}</DialogContentText></DialogContent>
      <DialogActions>
        <Button onClick={onCancelar}>{t('comun.cancelar')}</Button>
        <Button onClick={onConfirmar} color="error" variant="contained" autoFocus>{t('comun.eliminar')}</Button>
      </DialogActions>
    </Dialog>
  );
}
