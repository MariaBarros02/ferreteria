/*
 * Nombre: ConteoDetallePage.jsx
 * Descripción: Detalle de una sesión de conteo físico (RF-15): registro de cantidades contadas (selector de producto y lector de código), diferencias contra el sistema, cierre de la sesión y aprobación (genera ajustes de conteo) o rechazo con motivo.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import MotivoDialog from '../../components/MotivoDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import ProductoSelector from '../../components/ProductoSelector';
import useCarga from '../../hooks/useCarga';
import { cantidad, fechaHora } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

const NO_NEGATIVO = /^\d+([.,]\d{1,4})?$/;

export default function ConteoDetallePage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const { rol, usuario } = useAuth();
  const notificar = useNotify();
  const { datos, cargando, error, recargar } = useCarga(() => api.conteos.obtener(id), [id]);
  const [s, setS] = useState(null);
  const [producto, setProducto] = useState(null);
  const [contada, setContada] = useState('');
  const [obs, setObs] = useState('');
  const [intentado, setIntentado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [dialogo, setDialogo] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const campoCantidad = useRef(null);
  useEffect(() => { if (datos) setS(datos); }, [datos]);
  useEffect(() => { if (producto) campoCantidad.current?.focus(); }, [producto]);

  const volver = <Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to="/conteos" sx={{ mb: 1 }}>{t('conteo.volver')}</Button>;
  if (error && !s) return (<>{volver}<Alert severity="error" role="alert" action={<Button onClick={recargar}>{t('comun.reintentar')}</Button>}>{mensajeDeError(t, error)}</Alert></>);
  if (!s) return <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label={t('comun.cargando')} /></Box>;

  const abierta = s.estado === 'ABIERTA';
  const errores = {
    producto: !producto ? t('errores.obligatorio') : null,
    contada: !String(contada).trim() ? t('errores.obligatorio') : !NO_NEGATIVO.test(String(contada).trim()) ? t('errores.noNegativo') : null,
    obs: obs && !SIN_HTML.test(obs) ? t('errores.sinHtml') : null,
  };
  const registrar = async (ev) => {
    ev.preventDefault();
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setGuardando(true);
    try {
      setS(await api.conteos.registrar(s.id, { productoId: producto.id, cantidadContada: numero(contada), observacion: obs.trim() || null }));
      notificar(t('conteo.registrado', { producto: producto.nombre }));
      setProducto(null); setContada(''); setObs(''); setIntentado(false);
    } catch (x) { notificar(mensajeDeError(t, x), 'error'); }
    finally { setGuardando(false); }
  };
  const accion = async (fn, msg) => {
    setEnviando(true);
    try { setS(await fn()); notificar(msg); setDialogo(null); }
    catch (x) { notificar(mensajeDeError(t, x), 'error'); }
    finally { setEnviando(false); }
  };
  const propia = Boolean(s.usuario && usuario?.correo && s.usuario.toLowerCase() === usuario.correo.toLowerCase());
  const e = (k) => (intentado ? errores[k] : null);

  return (
    <section aria-labelledby="conteo-titulo">
      <PageHeader id="conteo-titulo" volver={volver} titulo={t('conteo.detalleTitulo', { numero: s.numero })}
        descripcion={<><EstadoChip estado={s.estado} /> {t('conteo.iniciadaPor', { usuario: s.usuario, fecha: fechaHora(s.fechaInicio) })}</>}
        acciones={<>
          {abierta && puede(rol, 'conteos.contar') && <Button variant="outlined" onClick={() => setDialogo('cerrar')} disabled={!s.lineas.length}>{t('conteo.cerrar')}</Button>}
          {s.estado === 'CERRADA' && puede(rol, 'conteos.decidir') && !propia && <>
            <Button variant="contained" color="success" onClick={() => setDialogo('aprobar')}>{t('comun.aprobar')}</Button>
            <Button variant="outlined" color="error" onClick={() => setDialogo('rechazar')}>{t('comun.rechazar')}</Button>
          </>}
        </>} />
      {s.estado === 'CERRADA' && propia && puede(rol, 'conteos.decidir') && <Alert severity="info" sx={{ mb: 2 }}>{t('conteo.noAutoaprobar')}</Alert>}
      {s.motivoRechazo && <Alert severity="warning" sx={{ mb: 2 }}>{t('conteo.motivoRechazo')}: {s.motivoRechazo}</Alert>}
      {s.observaciones && <Typography sx={{ mb: 2 }}>{t('comun.observaciones')}: {s.observaciones}</Typography>}
      {abierta && puede(rol, 'conteos.contar') && (
        <Paper component="form" onSubmit={registrar} sx={{ p: 2, mb: 2 }} aria-labelledby="conteo-registro">
          <Typography id="conteo-registro" variant="h6" component="h2">{t('conteo.registrar')}</Typography>
          <Typography variant="body2" color="text.secondary">{t('conteo.ciego')}</Typography>
          <ProductoSelector id="conteo-producto" valor={producto} onCambio={setProducto} requerido error={e('producto')} mostrarPrecio={false}
            textoAyuda={t('selector.lectorAyuda')} autoFocus />
          <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ sm: 'flex-start' }}>
            <TextField inputRef={campoCantidad} label={t('conteo.cantidadContada')} value={contada} onChange={(x) => setContada(x.target.value)} required margin="dense"
              inputProps={{ inputMode: 'decimal' }} error={Boolean(e('contada'))} helperText={e('contada')} />
            <TextField label={t('conteo.observacion')} value={obs} onChange={(x) => setObs(x.target.value.slice(0, 255))} margin="dense" fullWidth
              error={Boolean(e('obs'))} helperText={e('obs')} />
            <Button type="submit" variant="contained" sx={{ mt: { sm: 1.5 } }} disabled={guardando}>{t('comun.guardar')}</Button>
          </Stack>
        </Paper>)}
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('conteo.lineas')}>
            <TableHead><TableRow>
              <TableCell>{t('venta.producto')}</TableCell>
              {!abierta && <TableCell align="right">{t('conteo.sistema')}</TableCell>}
              <TableCell align="right">{t('conteo.contada')}</TableCell>
              {!abierta && <TableCell align="right">{t('conteo.diferencia')}</TableCell>}
              {!abierta && <TableCell align="right">{t('conteo.saldoActual')}</TableCell>}
              <TableCell>{t('conteo.observacion')}</TableCell>
              <TableCell>{t('comun.estado')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {!s.lineas.length && <TableRow><TableCell colSpan={7} align="center">{t('conteo.sinRegistros')}</TableCell></TableRow>}
              {s.lineas.map((l) => (
                <TableRow key={l.id}>
                  <TableCell>{l.producto}<Typography variant="caption" color="text.secondary" component="div">{l.codigoBarras}</Typography></TableCell>
                  {!abierta && <TableCell align="right">{cantidad(l.cantidadSistema)}</TableCell>}
                  <TableCell align="right">{cantidad(l.cantidadContada)}</TableCell>
                  {!abierta && <TableCell align="right" sx={{ color: Number(l.diferencia) ? (Number(l.diferencia) > 0 ? 'success.main' : 'error.main') : undefined, fontWeight: 600 }}>{cantidad(l.diferencia)}</TableCell>}
                  {!abierta && <TableCell align="right">{cantidad(l.saldoActual)}</TableCell>}
                  <TableCell>{l.observacion}</TableCell>
                  <TableCell><EstadoChip estado={l.estado} />{l.ajuste && <Typography variant="caption" component="div">{l.ajuste}</Typography>}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
      <ConfirmDialog abierto={dialogo === 'cerrar'} titulo={t('conteo.cerrarTitulo')} mensaje={t('conteo.cerrarMensaje', { n: s.lineas.length })} color="primary"
        textoConfirmar={t('conteo.cerrar')} cargando={enviando} onCancelar={() => setDialogo(null)}
        onConfirmar={() => accion(() => api.conteos.cerrar(s.id), t('conteo.cerrada'))} />
      <ConfirmDialog abierto={dialogo === 'aprobar'} titulo={t('conteo.aprobarTitulo')} mensaje={t('conteo.aprobarMensaje', { n: s.productosConDiferencia })} color="success"
        textoConfirmar={t('comun.aprobar')} cargando={enviando} onCancelar={() => setDialogo(null)}
        onConfirmar={() => accion(() => api.conteos.aprobar(s.id), t('conteo.aprobada'))} />
      <MotivoDialog abierto={dialogo === 'rechazar'} titulo={t('conteo.rechazarTitulo')} textoConfirmar={t('comun.rechazar')} onCancelar={() => setDialogo(null)}
        onConfirmar={(m) => accion(() => api.conteos.rechazar(s.id, m), t('conteo.rechazada'))} />
    </section>
  );
}
