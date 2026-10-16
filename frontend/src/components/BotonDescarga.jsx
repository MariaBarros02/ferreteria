/*
 * Nombre: BotonDescarga.jsx
 * Descripción: Botón que descarga un archivo de la API (Excel) mostrando el progreso y los errores.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { Button, CircularProgress } from '@mui/material';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import { useTranslation } from 'react-i18next';
import { useNotify } from './Notifier';
import { guardarBlob } from '../utils/archivos';
import { mensajeDeError } from '../utils/mensajes';

export default function BotonDescarga({ obtener, children, variant = 'outlined', size = 'medium' }) {
  const { t } = useTranslation();
  const notificar = useNotify();
  const [cargando, setCargando] = useState(false);
  const descargar = async () => {
    setCargando(true);
    try {
      const { blob, nombre } = await obtener();
      guardarBlob(blob, nombre);
    } catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setCargando(false); }
  };
  return (
    <Button variant={variant} size={size} onClick={descargar} disabled={cargando}
      startIcon={cargando ? <CircularProgress size={16} /> : <FileDownloadIcon />}>{children || t('comun.exportarExcel')}</Button>
  );
}
