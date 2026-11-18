/*
 * Nombre: AsistenteVirtual.jsx
 * Descripción: Panel del asistente virtual (chatbot de soporte): conversación con el motor de reglas del backend, temas sugeridos como botones, enlaces a las pantallas permitidas, indicador de escritura, aviso sin conexión y región accesible (aria-live). La conversación vive en memoria mientras dura la sesión.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Alert, Box, Button, Chip, CircularProgress, Divider, Drawer, IconButton, Paper, Stack, TextField, Toolbar,
  Tooltip, Typography } from '@mui/material';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import SendIcon from '@mui/icons-material/Send';
import CloseIcon from '@mui/icons-material/Close';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useTranslation } from 'react-i18next';
import { api } from '../api/http';
import useEnLinea from '../hooks/useEnLinea';
import { mensajeDeError } from '../utils/mensajes';

/** Longitud máxima de una pregunta (igual que la validación del servidor). */
export const MAX_PREGUNTA = 300;

let secuencia = 0;
const nuevoId = () => { secuencia += 1; return `m${secuencia}`; };

/**
 * Burbuja de un mensaje del asistente o del usuario.
 * @param {{mensaje: object, onOpcion: Function, onEnlace: Function, deshabilitado: boolean}} props - Mensaje y acciones.
 * @returns {JSX.Element} Mensaje.
 */
function Mensaje({ mensaje, onOpcion, onEnlace, deshabilitado }) {
  const { t } = useTranslation();
  const esUsuario = mensaje.autor === 'usuario';
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: esUsuario ? 'flex-end' : 'flex-start' }}
      data-testid={esUsuario ? 'mensaje-usuario' : 'mensaje-asistente'}>
      <Typography variant="caption" color="text.secondary" sx={{ px: 0.5 }}>
        {esUsuario ? t('asistente.tu') : t('asistente.nombre')}
      </Typography>
      <Paper elevation={0} sx={{
        px: 1.5, py: 1, maxWidth: '88%', borderRadius: 2, whiteSpace: 'pre-line', overflowWrap: 'anywhere',
        bgcolor: esUsuario ? 'primary.main' : (mensaje.error ? 'error.light' : 'action.hover'),
        color: esUsuario ? 'primary.contrastText' : 'text.primary',
      }}>
        <Typography variant="body2" component="p" sx={{ m: 0 }}>{mensaje.texto}</Typography>
      </Paper>
      {mensaje.enlace && (
        <Button size="small" component={RouterLink} to={mensaje.enlace.ruta} onClick={onEnlace}
          endIcon={<ArrowForwardIcon fontSize="small" />} sx={{ mt: 0.5 }}>
          {mensaje.enlace.texto}
        </Button>
      )}
      {mensaje.opciones?.length > 0 && (
        <Stack direction="row" useFlexGap flexWrap="wrap" spacing={0.75} sx={{ mt: 0.75, maxWidth: '100%' }}
          role="group" aria-label={t('asistente.opciones')}>
          {mensaje.opciones.map((o) => (
            <Chip key={o.id} label={o.titulo} variant="outlined" color="primary" size="small" clickable
              disabled={deshabilitado} onClick={() => onOpcion(o)}
              sx={{ height: 'auto', py: 0.5, '& .MuiChip-label': { whiteSpace: 'normal' } }} />
          ))}
        </Stack>
      )}
    </Box>
  );
}

/**
 * Panel lateral del asistente virtual.
 * @param {{abierto: boolean, onCerrar: Function}} props - Visibilidad y cierre.
 * @returns {JSX.Element} Panel.
 */
export default function AsistenteVirtual({ abierto, onCerrar }) {
  const { t } = useTranslation();
  const enLinea = useEnLinea();
  const [mensajes, setMensajes] = useState([]);
  const [texto, setTexto] = useState('');
  const [cargando, setCargando] = useState(false);
  const [iniciado, setIniciado] = useState(false);
  const fin = useRef(null);
  const entrada = useRef(null);

  const agregar = (m) => setMensajes((ms) => [...ms, { id: nuevoId(), ...m }]);

  const responder = useCallback(async (peticion) => {
    setCargando(true);
    try {
      const r = await peticion();
      agregar({ autor: 'asistente', texto: r.texto, opciones: r.opciones || [], enlace: r.enlace || null, tipo: r.tipo });
    } catch (e) {
      const sinConexion = e?.code === 'OFFLINE' || (typeof navigator !== 'undefined' && navigator.onLine === false);
      agregar({ autor: 'asistente', error: true, texto: sinConexion ? t('asistente.sinConexion') : mensajeDeError(t, e) });
    } finally {
      setCargando(false);
    }
  }, [t]);

  // La bienvenida se pide la primera vez que se abre el panel con conexión.
  useEffect(() => {
    if (abierto && !iniciado && enLinea) {
      setIniciado(true);
      responder(() => api.asistente.inicio());
    }
  }, [abierto, iniciado, enLinea, responder]);

  useEffect(() => { fin.current?.scrollIntoView?.({ block: 'end' }); }, [mensajes, cargando]);

  const enviarTexto = (e) => {
    e.preventDefault();
    const pregunta = texto.trim();
    if (!pregunta || cargando) return;
    agregar({ autor: 'usuario', texto: pregunta });
    setTexto('');
    responder(() => api.asistente.enviar({ texto: pregunta }));
    entrada.current?.focus();
  };

  const elegir = (opcion) => {
    if (cargando) return;
    agregar({ autor: 'usuario', texto: opcion.titulo });
    responder(() => api.asistente.enviar({ nodo: opcion.id }));
  };

  const reiniciar = () => {
    setMensajes([]);
    setIniciado(false);   // vuelve a pedir la bienvenida
  };

  return (
    <Drawer anchor="right" open={abierto} onClose={onCerrar}
      sx={{ zIndex: (tema) => tema.zIndex.modal }}   // por encima de la barra superior (zIndex drawer + 1)
      PaperProps={{ sx: { width: { xs: '100%', sm: 400 }, display: 'flex' }, 'aria-labelledby': 'asistente-titulo', role: 'dialog' }}>
      <Toolbar sx={{ gap: 1, bgcolor: 'primary.main', color: 'primary.contrastText' }}>
        <SmartToyIcon aria-hidden />
        <Typography id="asistente-titulo" variant="h6" component="h2" sx={{ flexGrow: 1 }} noWrap>
          {t('asistente.titulo')}
        </Typography>
        <Tooltip title={t('asistente.reiniciar')}>
          <span>
            <IconButton color="inherit" aria-label={t('asistente.reiniciar')} onClick={reiniciar} disabled={cargando}>
              <RestartAltIcon />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title={t('asistente.cerrar')}>
          <IconButton color="inherit" edge="end" aria-label={t('asistente.cerrar')} onClick={onCerrar}><CloseIcon /></IconButton>
        </Tooltip>
      </Toolbar>
      {!enLinea && <Alert severity="warning" square role="status">{t('asistente.sinConexion')}</Alert>}
      <Box role="log" aria-live="polite" aria-label={t('asistente.conversacion')}
        sx={{ flexGrow: 1, overflowY: 'auto', p: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        {mensajes.map((m) => (
          <Mensaje key={m.id} mensaje={m} onOpcion={elegir} onEnlace={onCerrar} deshabilitado={cargando || !enLinea} />
        ))}
        {cargando && (
          <Stack direction="row" spacing={1} alignItems="center" role="status">
            <CircularProgress size={16} aria-hidden />
            <Typography variant="caption" color="text.secondary">{t('asistente.escribiendo')}</Typography>
          </Stack>
        )}
        <div ref={fin} />
      </Box>
      <Divider />
      <Box sx={{ px: 2, pt: 1 }}>
        <Chip label={t('asistente.menu')} size="small" variant="outlined" clickable disabled={cargando || !enLinea}
          onClick={() => elegir({ id: 'saludo', titulo: t('asistente.menu') })} />
      </Box>
      <Box component="form" onSubmit={enviarTexto} sx={{ display: 'flex', gap: 1, p: 2, alignItems: 'flex-start' }}>
        <TextField fullWidth size="small" autoFocus inputRef={entrada} value={texto} autoComplete="off"
          onChange={(e) => setTexto(e.target.value.slice(0, MAX_PREGUNTA))}
          label={t('asistente.pregunta')} placeholder={t('asistente.ejemplo')} disabled={!enLinea}
          inputProps={{ maxLength: MAX_PREGUNTA }}
          helperText={`${texto.length}/${MAX_PREGUNTA}`} FormHelperTextProps={{ sx: { textAlign: 'right', mr: 0 } }} />
        <IconButton type="submit" color="primary" aria-label={t('asistente.enviar')}
          disabled={!texto.trim() || cargando || !enLinea} sx={{ mt: 0.5 }}>
          <SendIcon />
        </IconButton>
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ px: 2, pb: 1.5 }}>{t('asistente.aviso')}</Typography>
    </Drawer>
  );
}
