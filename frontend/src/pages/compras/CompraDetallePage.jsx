/*
 * Nombre: CompraDetallePage.jsx
 * Descripción: Detalle de una orden de compra (RF-03, RF-17): líneas con pedido/recibido/pendiente, recepciones parciales con lote y vencimiento, documentos de ingreso con anulación (motivo obligatorio), anulación de la orden y devolución al proveedor.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, Paper, Stack,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import EstadoChip from '../../components/EstadoChip';
import MotivoDialog from '../../components/MotivoDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import useCarga from '../../hooks/useCarga';
import { nuevaClaveOperacion } from '../../utils/archivos';
import { cantidad, fecha, fechaHora, hoyIso, moneda } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { errorCantidad, numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';
import { Dato } from '../ventas/VentaDetallePage';

/** Diálogo de recepción parcial o total. */
export function RecepcionDialog({ abierto, compra, onCerrar, onRecibida }) {
  const { t } = useTranslation();
  const [factura, setFactura] = useState('');
  const [obs, setObs] = useState('');
  const [filas, setFilas] = useState({});
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  const clave = useRef(nuevaClaveOperacion());
  const pendientes = (compra?.lineas || []).filter((l) => Number(l.cantidadPendiente) > 0);

  useEffect(() => {
    if (!abierto) return;
    setFactura(''); setObs(''); setIntentado(false); setFalla(null); clave.current = nuevaClaveOperacion();
    setFilas(Object.fromEntries(pendientes.map((l) => [l.id, { cantidad: '', codigoLote: '', fechaVencimiento: '' }])));
  }, [abierto]); // eslint-disable-line react-hooks/exhaustive-deps

  const editar = (id, c, v) => setFilas((f) => ({ ...f, [id]: { ...f[id], [c]: v } }));
  const errores = {};
  pendientes.forEach((l) => {
    const f = filas[l.id];
    if (!f || !String(f.cantidad).trim()) return;
    const e = {};
    const c = errorCantidad(f.cantidad);
    if (c) e.cantidad = t(c);
    else if (numero(f.cantidad) > Number(l.cantidadPendiente)) e.cantidad = t('compra.excedePendiente', { pendiente: cantidad(l.cantidadPendiente) });
    if (l.tipoRastreo === 'LOTE' && !f.codigoLote.trim()) e.codigoLote = t('errores.obligatorio');
    if (f.codigoLote && !SIN_HTML.test(f.codigoLote)) e.codigoLote = t('errores.sinHtml');
    if (l.tieneVencimiento && !f.fechaVencimiento) e.fechaVencimiento = t('errores.obligatorio');
    if (f.fechaVencimiento && f.fechaVencimiento < hoyIso()) e.fechaVencimiento = t('compra.loteVencido');
    if (Object.keys(e).length) errores[l.id] = e;
  });
  const seleccionadas = pendientes.filter((l) => String(filas[l.id]?.cantidad || '').trim());
  const errorFactura = !factura.trim() ? t('errores.obligatorio') : !SIN_HTML.test(factura) ? t('errores.sinHtml') : null;
  const valido = !errorFactura && seleccionadas.length && !Object.keys(errores).length;

  const enviar = async () => {
    setIntentado(true);
    if (!valido) return;
    setEnviando(true); setFalla(null);
    try {
      const r = await api.compras.recibir(compra.id, {
        facturaProveedor: factura.trim(), observaciones: obs.trim() || null, idOperacion: clave.current,
        lineas: seleccionadas.map((l) => {
          const f = filas[l.id];
          return { idLineaCompra: l.id, cantidad: numero(f.cantidad), codigoLote: f.codigoLote.trim() || null, fechaVencimiento: f.fechaVencimiento || null };
        }),
      });
      onRecibida(r);
    } catch (e) { setFalla(mensajeDeError(t, e)); }
    finally { setEnviando(false); }
  };

  return (
    <Dialog open={abierto} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="lg" aria-labelledby="recepcion-titulo">
      <DialogTitle id="recepcion-titulo">{t('compra.recibirTitulo', { numero: compra?.numero })}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{t('compra.recibirAyuda')}</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2}>
          <TextField label={t('compra.facturaProveedor')} value={factura} onChange={(e) => setFactura(e.target.value.slice(0, 100))} required
            error={intentado && Boolean(errorFactura)} helperText={intentado ? errorFactura : null} margin="dense" autoFocus />
          <TextField label={t('comun.observaciones')} value={obs} onChange={(e) => setObs(e.target.value.slice(0, 255))} margin="dense" fullWidth />
        </Stack>
        <TableContainer>
          <Table size="small" aria-label={t('compra.lineasPendientes')}>
            <TableHead><TableRow>
              <TableCell>{t('venta.producto')}</TableCell>
              <TableCell align="right">{t('compra.pendiente')}</TableCell>
              <TableCell>{t('compra.cantidadRecibir')}</TableCell>
              <TableCell>{t('inventario.lote')}</TableCell>
              <TableCell>{t('inventario.vencimiento')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {pendientes.map((l) => {
                const f = filas[l.id] || {};
                const e = intentado ? errores[l.id] || {} : errores[l.id]?.cantidad ? { cantidad: errores[l.id].cantidad } : {};
                return (
                  <TableRow key={l.id}>
                    <TableCell>{l.producto}<Typography variant="caption" component="div" color="text.secondary">{l.codigoBarras}</Typography></TableCell>
                    <TableCell align="right">{cantidad(l.cantidadPendiente)} {l.unidadBase}</TableCell>
                    <TableCell sx={{ minWidth: 120 }}>
                      <Stack direction="row" gap={0.5} alignItems="flex-start">
                        <TextField size="small" value={f.cantidad ?? ''} onChange={(ev) => editar(l.id, 'cantidad', ev.target.value)}
                          inputProps={{ inputMode: 'decimal', 'aria-label': `${t('compra.cantidadRecibir')} ${l.producto}` }}
                          error={Boolean(e.cantidad)} helperText={e.cantidad} />
                        <Button size="small" onClick={() => editar(l.id, 'cantidad', String(Number(l.cantidadPendiente)))}>{t('compra.todo')}</Button>
                      </Stack>
                    </TableCell>
                    <TableCell sx={{ minWidth: 140 }}>
                      {l.tipoRastreo === 'LOTE' ? (
                        <TextField size="small" value={f.codigoLote ?? ''} onChange={(ev) => editar(l.id, 'codigoLote', ev.target.value.slice(0, 50))}
                          inputProps={{ 'aria-label': `${t('inventario.lote')} ${l.producto}` }} error={Boolean(e.codigoLote)} helperText={e.codigoLote} />
                      ) : <Typography variant="caption" color="text.secondary">{t('compra.sinLote')}</Typography>}
                    </TableCell>
                    <TableCell sx={{ minWidth: 160 }}>
                      {l.tieneVencimiento ? (
                        <TextField size="small" type="date" value={f.fechaVencimiento ?? ''} onChange={(ev) => editar(l.id, 'fechaVencimiento', ev.target.value)}
                          inputProps={{ min: hoyIso(), 'aria-label': `${t('inventario.vencimiento')} ${l.producto}` }}
                          error={Boolean(e.fechaVencimiento)} helperText={e.fechaVencimiento} />
                      ) : <Typography variant="caption" color="text.secondary">{t('compra.noVence')}</Typography>}
                    </TableCell>
                  </TableRow>);
              })}
            </TableBody>
          </Table>
        </TableContainer>
        {intentado && !seleccionadas.length && <Typography color="error" role="alert" sx={{ mt: 1 }}>{t('compra.indiqueCantidad')}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando}
          startIcon={enviando ? <CircularProgress size={16} color="inherit" /> : null}>{t('compra.confirmarRecepcion')}</Button>
      </DialogActions>
    </Dialog>
  );
}

/** Diálogo de devolución al proveedor desde la compra. */
function DevolucionProveedorDialog({ abierto, compra, onCerrar, onHecha }) {
  const { t } = useTranslation();
  const [motivo, setMotivo] = useState('');
  const [tratamiento, setTratamiento] = useState('NOTA_CREDITO_PROVEEDOR');
  const [filas, setFilas] = useState({});
  const [lotes, setLotes] = useState({});
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  const clave = useRef(nuevaClaveOperacion());
  const recibidas = (compra?.lineas || []).filter((l) => Number(l.cantidadRecibida) > 0);

  useEffect(() => {
    if (!abierto) return;
    setMotivo(''); setTratamiento('NOTA_CREDITO_PROVEEDOR'); setIntentado(false); setFalla(null); clave.current = nuevaClaveOperacion();
    setFilas(Object.fromEntries(recibidas.map((l) => [l.id, { cantidad: '', origen: 'DISPONIBLE', idLote: '' }])));
    recibidas.filter((l) => l.tipoRastreo === 'LOTE').forEach((l) => {
      api.inventario.lotes(l.productoId).then((ls) => setLotes((x) => ({ ...x, [l.productoId]: ls }))).catch(() => {});
    });
  }, [abierto]); // eslint-disable-line react-hooks/exhaustive-deps

  const editar = (id, c, v) => setFilas((f) => ({ ...f, [id]: { ...f[id], [c]: v } }));
  const elegidas = recibidas.filter((l) => String(filas[l.id]?.cantidad || '').trim());
  const errores = {};
  elegidas.forEach((l) => {
    const c = errorCantidad(filas[l.id].cantidad);
    if (c) errores[l.id] = t(c);
    else if (numero(filas[l.id].cantidad) > Number(l.cantidadRecibida)) errores[l.id] = t('devolucion.excedeRecibido');
  });
  const errorMotivo = !motivo.trim() ? t('errores.obligatorio') : !SIN_HTML.test(motivo) ? t('errores.sinHtml') : null;

  const enviar = async () => {
    setIntentado(true);
    if (errorMotivo || !elegidas.length || Object.keys(errores).length) return;
    setEnviando(true); setFalla(null);
    try {
      const d = await api.devoluciones.proveedor({
        compraId: compra.id, motivo: motivo.trim(), tratamiento, idOperacion: clave.current,
        lineas: elegidas.map((l) => ({ idLineaCompra: l.id, cantidad: numero(filas[l.id].cantidad), origen: filas[l.id].origen,
          ...(filas[l.id].idLote ? { idLote: Number(filas[l.id].idLote) } : {}) })),
      });
      onHecha(d);
    } catch (e) { setFalla(mensajeDeError(t, e)); }
    finally { setEnviando(false); }
  };

  return (
    <Dialog open={abierto} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="md" aria-labelledby="devprov-titulo">
      <DialogTitle id="devprov-titulo">{t('devolucion.proveedorTitulo', { numero: compra?.numero })}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2}>
          <TextField label={t('comun.motivo')} value={motivo} onChange={(e) => setMotivo(e.target.value.slice(0, 255))} required fullWidth margin="dense"
            error={intentado && Boolean(errorMotivo)} helperText={intentado ? errorMotivo : null} autoFocus />
          <TextField select label={t('devolucion.tratamiento')} value={tratamiento} onChange={(e) => setTratamiento(e.target.value)} margin="dense" sx={{ minWidth: 220 }}>
            {['NOTA_CREDITO_PROVEEDOR', 'CAMBIO', 'REEMBOLSO', 'NINGUNO'].map((x) => <MenuItem key={x} value={x}>{t(`devolucion.tratamientos.${x}`)}</MenuItem>)}
          </TextField>
        </Stack>
        <TableContainer>
          <Table size="small" aria-label={t('compra.lineas')}>
            <TableHead><TableRow>
              <TableCell>{t('venta.producto')}</TableCell><TableCell align="right">{t('compra.recibido')}</TableCell>
              <TableCell>{t('venta.cantidad')}</TableCell><TableCell>{t('devolucion.origen')}</TableCell><TableCell>{t('inventario.lote')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {recibidas.map((l) => {
                const f = filas[l.id] || {};
                return (
                  <TableRow key={l.id}>
                    <TableCell>{l.producto}</TableCell>
                    <TableCell align="right">{cantidad(l.cantidadRecibida)} {l.unidadBase}</TableCell>
                    <TableCell><TextField size="small" value={f.cantidad ?? ''} onChange={(e) => editar(l.id, 'cantidad', e.target.value)}
                      inputProps={{ inputMode: 'decimal', 'aria-label': `${t('venta.cantidad')} ${l.producto}` }} error={Boolean(errores[l.id])} helperText={errores[l.id]} /></TableCell>
                    <TableCell><TextField select size="small" value={f.origen ?? 'DISPONIBLE'} onChange={(e) => editar(l.id, 'origen', e.target.value)}
                      inputProps={{ 'aria-label': `${t('devolucion.origen')} ${l.producto}` }}>
                      <MenuItem value="DISPONIBLE">{t('inventario.estados.DISPONIBLE')}</MenuItem>
                      <MenuItem value="DANADO">{t('inventario.estados.DANADO')}</MenuItem>
                    </TextField></TableCell>
                    <TableCell>{l.tipoRastreo === 'LOTE' ? (
                      <TextField select size="small" value={f.idLote ?? ''} onChange={(e) => editar(l.id, 'idLote', e.target.value)} sx={{ minWidth: 140 }}
                        inputProps={{ 'aria-label': `${t('inventario.lote')} ${l.producto}` }}>
                        <MenuItem value="">{t('devolucion.loteAutomatico')}</MenuItem>
                        {(lotes[l.productoId] || []).map((x) => <MenuItem key={x.id} value={x.id}>{`${x.codigo} · ${cantidad(x.disponible)}`}</MenuItem>)}
                      </TextField>) : '—'}</TableCell>
                  </TableRow>);
              })}
            </TableBody>
          </Table>
        </TableContainer>
        {intentado && !elegidas.length && <Typography color="error" role="alert" sx={{ mt: 1 }}>{t('compra.indiqueCantidad')}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" color="warning" onClick={enviar} disabled={enviando}>{t('devolucion.registrar')}</Button>
      </DialogActions>
    </Dialog>
  );
}

export default function CompraDetallePage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const { datos, cargando, error, recargar } = useCarga(() => api.compras.obtener(id), [id]);
  const [compra, setCompra] = useState(null);
  const [dialogo, setDialogo] = useState(null);   // 'recibir' | 'anular' | 'devolver' | { ingreso }
  useEffect(() => { if (datos) setCompra(datos); }, [datos]);

  const volver = <Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to="/compras" sx={{ mb: 1 }}>{t('compra.volverListado')}</Button>;
  if (error && !compra) return (<>{volver}<Alert severity="error" role="alert" action={<Button onClick={recargar}>{t('comun.reintentar')}</Button>}>{mensajeDeError(t, error)}</Alert></>);
  if (!compra) return <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label={t('comun.cargando')} /></Box>;
  const c = compra;
  const activa = c.estado === 'ACTIVO';
  const conPendientes = c.lineas.some((l) => Number(l.cantidadPendiente) > 0);
  const conRecibido = c.lineas.some((l) => Number(l.cantidadRecibida) > 0);
  const sinIngresosActivos = !c.documentos.some((d) => d.estado === 'ACTIVO');

  const anular = async (motivo) => {
    try { setCompra(await api.compras.anular(c.id, motivo)); notificar(t('compra.anulada')); setDialogo(null); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
  };
  const anularIngreso = async (motivo) => {
    try { setCompra(await api.compras.anularIngreso(dialogo.ingreso.id, motivo)); notificar(t('compra.ingresoAnulado')); setDialogo(null); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
  };

  return (
    <section aria-labelledby="compra-detalle-titulo">
      <PageHeader id="compra-detalle-titulo" volver={volver} titulo={t('compra.detalleTitulo', { numero: c.numero })}
        descripcion={<><EstadoChip estado={c.estado} /> <EstadoChip estado={c.estadoRecepcion} /> {fechaHora(c.fecha)}</>}
        acciones={<>
          {activa && conPendientes && puede(rol, 'compras.recibir') && <Button variant="contained" onClick={() => setDialogo('recibir')}>{t('compra.recibir')}</Button>}
          {activa && conRecibido && puede(rol, 'devoluciones.proveedor') && <Button variant="outlined" color="warning" onClick={() => setDialogo('devolver')}>{t('devolucion.alProveedor')}</Button>}
          {activa && sinIngresosActivos && puede(rol, 'compras.anular') && <Button variant="outlined" color="error" onClick={() => setDialogo('anular')}>{t('compra.anular')}</Button>}
        </>} />
      {c.motivoAnulacion && <Alert severity="info" sx={{ mb: 2 }}>{t('compra.motivoAnulacion')}: {c.motivoAnulacion}</Alert>}
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2 }}>
            <Stack gap={1}>
              <Dato etiqueta={t('compra.proveedor')}>{c.proveedor} ({c.proveedorIdentificacion})</Dato>
              <Dato etiqueta={t('compra.usuario')}>{c.usuario}</Dato>
              <Dato etiqueta={t('comun.observaciones')}>{c.observaciones}</Dato>
            </Stack>
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2 }}>
            <Stack gap={1}>
              <Dato etiqueta={t('venta.subtotal')}>{moneda(c.subtotal, 2)}</Dato>
              <Dato etiqueta={t('venta.descuentos')}>{moneda(c.descuentos, 2)}</Dato>
              <Dato etiqueta={t('venta.impuestos')}>{moneda(c.impuestos, 2)}</Dato>
              <Dato etiqueta={t('compra.total')}><strong>{moneda(c.total, 2)}</strong></Dato>
            </Stack>
          </Paper>
        </Grid>
      </Grid>
      <Paper sx={{ mb: 2 }}>
        <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{t('compra.lineas')}</Typography>
        <TableContainer>
          <Table size="small" aria-label={t('compra.lineas')}>
            <TableHead><TableRow>
              <TableCell>{t('venta.producto')}</TableCell>
              <TableCell align="right">{t('compra.pedido')}</TableCell>
              <TableCell align="right">{t('compra.recibido')}</TableCell>
              <TableCell align="right">{t('compra.pendiente')}</TableCell>
              <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('compra.costoUnitario')}</TableCell>
              <TableCell align="right">{t('compra.total')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {c.lineas.map((l) => (
                <TableRow key={l.id}>
                  <TableCell>{l.producto}<Typography variant="caption" color="text.secondary" component="div">{l.codigoBarras}{l.unidad ? ` · ${cantidad(l.cantidadPresentacion)} ${l.unidad}` : ''}</Typography></TableCell>
                  <TableCell align="right">{cantidad(l.cantidadPedida)} {l.unidadBase}</TableCell>
                  <TableCell align="right">{cantidad(l.cantidadRecibida)}</TableCell>
                  <TableCell align="right">{cantidad(l.cantidadPendiente)}</TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{moneda(l.costoUnitario, 2)}</TableCell>
                  <TableCell align="right">{moneda(l.total, 2)}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>{t('compra.documentosIngreso')}</Typography>
        {!c.documentos.length && <Typography color="text.secondary">{t('compra.sinIngresos')}</Typography>}
        <Stack gap={2}>
          {c.documentos.map((d) => (
            <Paper key={d.id} variant="outlined" sx={{ p: 2 }}>
              <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}>
                <Typography sx={{ fontWeight: 600 }}>{d.consecutivo} · {t('compra.facturaProveedor')}: {d.facturaProveedor} · {fechaHora(d.fecha)} · {d.usuario} <EstadoChip estado={d.estado} /></Typography>
                {d.estado === 'ACTIVO' && puede(rol, 'compras.anularIngreso') && (
                  <Button size="small" color="error" onClick={() => setDialogo({ ingreso: d })}>{t('compra.anularIngreso')}</Button>)}
              </Stack>
              {d.motivoAnulacion && <Typography variant="body2" color="text.secondary">{t('compra.motivoAnulacion')}: {d.motivoAnulacion}</Typography>}
              <Box component="ul" sx={{ m: 0, pl: 3 }}>
                {d.recepciones.map((r) => (
                  <li key={r.id}><Typography variant="body2">{r.producto}: {cantidad(r.cantidad)}{r.lote ? ` · ${t('inventario.lote')} ${r.lote}` : ''}{r.fechaVencimiento ? ` · ${t('inventario.vence')} ${fecha(r.fechaVencimiento)}` : ''} · {moneda(r.costoUnitario, 2)}</Typography></li>))}
              </Box>
            </Paper>))}
        </Stack>
      </Paper>
      <RecepcionDialog abierto={dialogo === 'recibir'} compra={c} onCerrar={() => setDialogo(null)}
        onRecibida={(r) => { setCompra(r); setDialogo(null); notificar(t('compra.recibida')); }} />
      <DevolucionProveedorDialog abierto={dialogo === 'devolver'} compra={c} onCerrar={() => setDialogo(null)}
        onHecha={(d) => { setDialogo(null); notificar(t('devolucion.creada', { numero: d.numero })); recargar(); }} />
      <MotivoDialog abierto={dialogo === 'anular'} titulo={t('compra.anularTitulo', { numero: c.numero })} mensaje={t('compra.anularMensaje')}
        textoConfirmar={t('compra.anular')} onConfirmar={anular} onCancelar={() => setDialogo(null)} />
      <MotivoDialog abierto={Boolean(dialogo?.ingreso)} titulo={t('compra.anularIngresoTitulo', { numero: dialogo?.ingreso?.consecutivo })}
        mensaje={t('compra.anularIngresoMensaje')} textoConfirmar={t('compra.anularIngreso')} onConfirmar={anularIngreso} onCancelar={() => setDialogo(null)} />
    </section>
  );
}
