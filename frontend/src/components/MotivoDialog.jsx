/*
 * Nombre: MotivoDialog.jsx
 * Descripción: Diálogo que solicita un motivo obligatorio antes de una operación sensible (anular, rechazar, cancelar, reabrir).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useState } from 'react';
import { Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { SIN_HTML } from '../validation/schemas';

export default function MotivoDialog({ abierto, titulo, mensaje, textoConfirmar, color = 'error', onConfirmar, onCancelar }) {
  const { t } = useTranslation();
  const [motivo, setMotivo] = useState('');
  const [tocado, setTocado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  useEffect(() => { if (abierto) { setMotivo(''); setTocado(false); setEnviando(false); } }, [abierto]);
  const error = !motivo.trim() ? t('errores.obligatorio') : !SIN_HTML.test(motivo) ? t('errores.sinHtml') : null;
  const confirmar = async () => {
    setTocado(true);
    if (error) return;
    setEnviando(true);
    try { await onConfirmar(motivo.trim()); } finally { setEnviando(false); }
  };
  return (
    <Dialog open={abierto} onClose={enviando ? undefined : onCancelar} fullWidth maxWidth="sm" aria-labelledby="motivo-titulo">
      <DialogTitle id="motivo-titulo">{titulo}</DialogTitle>
      <DialogContent>
        {mensaje && <DialogContentText sx={{ mb: 1 }}>{mensaje}</DialogContentText>}
        <TextField label={t('comun.motivo')} value={motivo} onChange={(e) => setMotivo(e.target.value.slice(0, 255))}
          onBlur={() => setTocado(true)} required fullWidth multiline minRows={2} autoFocus
          error={tocado && Boolean(error)} helperText={tocado && error ? error : `${motivo.length}/255`} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancelar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button onClick={confirmar} color={color} variant="contained" disabled={enviando}
          startIcon={enviando ? <CircularProgress size={16} color="inherit" /> : null}>{textoConfirmar || t('comun.confirmar')}</Button>
      </DialogActions>
    </Dialog>
  );
}
