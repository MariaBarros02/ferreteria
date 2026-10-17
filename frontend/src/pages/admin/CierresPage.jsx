/*
 * Nombre: CierresPage.jsx
 * Descripción: Cierre operativo mensual (RF-08): resumen del periodo (ventas, devoluciones, compras recibidas, movimientos), conciliación inventario vs. kardex, pendientes que bloquean el cierre, cierre con observaciones, historial y reapertura con motivo. Solo Gerencia.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Grid, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import MotivoDialog from '../../components/MotivoDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { cantidad, fecha, fechaHora, mesActual, moneda } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { SIN_HTML } from '../../validation/schemas';

function Indicador({ titulo, valor, detalle }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}><CardContent>
      <Typography variant="overline" component="p">{titulo}</Typography>
      <Typography variant="h5" component="p">{valor}</Typography>
      {detalle && <Typography variant="body2" color="text.secondary">{detalle}</Typography>}
    </CardContent></Card>
  );
}

export default function CierresPage() {
  const { t } = useTranslation();
  const notificar = useNotify();
  const [periodo, setPeriodo] = useState(mesActual());
  const [obs, setObs] = useState('');
  const [dialogo, setDialogo] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const periodoValido = /^\d{4}-(0[1-9]|1[0-2])$/.test(periodo);
  const resumen = useCarga(() => (periodoValido ? api.cierres.resumen(periodo) : Promise.resolve(null)), [periodo]);
  const historial = useCarga(() => api.cierres.listar(), []);
  const r = resumen.datos;
  const cerrado = r?.cierre?.estado === 'CERRADO';
  const errorObs = obs && !SIN_HTML.test(obs);

  const cerrar = async () => {
    setEnviando(true);
    try { await api.cierres.cerrar({ periodo, observaciones: obs.trim() || null }); notificar(t('cierre.cerrado', { periodo })); setDialogo(null); setObs(''); resumen.recargar(); historial.recargar(); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); setDialogo(null); }
    finally { setEnviando(false); }
  };
  const reabrir = async (motivo) => {
    try { await api.cierres.reabrir(dialogo.periodo, motivo); notificar(t('cierre.reabierto', { periodo: dialogo.periodo })); setDialogo(null); resumen.recargar(); historial.recargar(); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
  };
  const diferencias = r?.diferenciasConciliacion || [];
  const requiereObs = diferencias.length > 0 && !obs.trim();

  return (
    <section aria-labelledby="cierres-titulo">
      <PageHeader id="cierres-titulo" titulo={t('cierre.titulo')} descripcion={t('cierre.descripcion')} />
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ sm: 'center' }}>
          <TextField type="month" label={t('cierre.periodo')} value={periodo} onChange={(e) => setPeriodo(e.target.value)} InputLabelProps={{ shrink: true }}
            error={!periodoValido} helperText={!periodoValido ? t('cierre.periodoInvalido') : null} size="small" />
          {r && <Typography>{fecha(r.desde)} – {fecha(r.hasta)} · <EstadoChip estado={r.estado} /></Typography>}
        </Stack>
      </Paper>
      {resumen.error && <Alert severity="error" role="alert" action={<Button onClick={resumen.recargar}>{t('comun.reintentar')}</Button>}>{mensajeDeError(t, resumen.error)}</Alert>}
      {r && (<>
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid item xs={12} sm={6} md={3}><Indicador titulo={t('cierre.ventas')} valor={moneda(r.totalVentas)} detalle={t('cierre.documentos', { n: r.numeroVentas })} /></Grid>
          <Grid item xs={12} sm={6} md={3}><Indicador titulo={t('cierre.devolucionesClientes')} valor={moneda(r.totalDevolucionesClientes)} detalle={t('cierre.documentos', { n: r.numeroDevolucionesClientes })} /></Grid>
          <Grid item xs={12} sm={6} md={3}><Indicador titulo={t('cierre.comprasRecibidas')} valor={moneda(r.valorComprasRecibidas)} detalle={t('cierre.documentos', { n: r.documentosIngreso })} /></Grid>
          <Grid item xs={12} sm={6} md={3}><Indicador titulo={t('cierre.devolucionesProveedor')} valor={moneda(r.totalDevolucionesProveedor)} detalle={t('cierre.documentos', { n: r.numeroDevolucionesProveedor })} /></Grid>
        </Grid>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('venta.subtotal')}: {moneda(r.subtotalVentas, 2)} · {t('venta.descuentos')}: {moneda(r.descuentosVentas, 2)} · {t('venta.impuestos')}: {moneda(r.impuestosVentas, 2)}
        </Typography>
        {r.bloqueantes?.length > 0 && (
          <Alert severity="warning" sx={{ mb: 2 }} role="alert">
            {t('cierre.bloqueantes')}
            <ul>{r.bloqueantes.map((b) => <li key={b}>{b}</li>)}</ul>
          </Alert>)}
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid item xs={12} md={6}>
            <Paper>
              <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{t('cierre.movimientos')}</Typography>
              <TableContainer><Table size="small" aria-label={t('cierre.movimientos')}>
                <TableHead><TableRow><TableCell>{t('movimiento.tipo')}</TableCell><TableCell>{t('movimiento.estado')}</TableCell><TableCell align="right">{t('cierre.registros')}</TableCell><TableCell align="right">{t('cierre.unidades')}</TableCell></TableRow></TableHead>
                <TableBody>
                  <TablaEstado columnas={4} vacio={!r.movimientos?.length} />
                  {(r.movimientos || []).map((m) => (
                    <TableRow key={`${m.tipo}-${m.estadoInventario}`}>
                      <TableCell>{t(`movimiento.tipos.${String(m.tipo).toUpperCase()}`, { defaultValue: m.tipo })}</TableCell>
                      <TableCell>{t(`inventario.estados.${String(m.estadoInventario).toUpperCase()}`, { defaultValue: m.estadoInventario })}</TableCell>
                      <TableCell align="right">{m.cantidad}</TableCell><TableCell align="right">{cantidad(m.unidades)}</TableCell>
                    </TableRow>))}
                </TableBody>
              </Table></TableContainer>
            </Paper>
          </Grid>
          <Grid item xs={12} md={6}>
            <Paper sx={{ mb: 2, p: 2 }}>
              <Typography variant="h6" component="h2" gutterBottom>{t('cierre.pendientes')}</Typography>
              <Typography variant="body2">{t('cierre.ajustesPendientes')}: {r.pendientes?.ajustesPendientes}</Typography>
              <Typography variant="body2">{t('cierre.conteosSinDecidir')}: {r.pendientes?.conteosSinDecidir}</Typography>
              <Typography variant="body2">{t('cierre.preciosPendientes')}: {r.pendientes?.preciosPendientes}</Typography>
              <Typography variant="body2">{t('cierre.reservasVencidas')}: {r.pendientes?.reservasVencidasActivas}</Typography>
            </Paper>
            <Paper>
              <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{t('cierre.conciliacion')}</Typography>
              {!diferencias.length ? <Typography sx={{ p: 2 }} color="success.main">{t('cierre.sinDiferencias')}</Typography> : (
                <TableContainer><Table size="small" aria-label={t('cierre.conciliacion')}>
                  <TableHead><TableRow><TableCell>{t('venta.producto')}</TableCell><TableCell align="right">{t('cierre.saldoRegistrado')}</TableCell><TableCell align="right">{t('cierre.saldoEsperado')}</TableCell></TableRow></TableHead>
                  <TableBody>{diferencias.map((d) => (
                    <TableRow key={d.productoId}><TableCell>{d.producto}</TableCell><TableCell align="right">{cantidad(d.saldoRegistrado)}</TableCell><TableCell align="right">{cantidad(d.saldoEsperado)}</TableCell></TableRow>))}
                  </TableBody>
                </Table></TableContainer>)}
            </Paper>
          </Grid>
        </Grid>
        {!cerrado && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <TextField label={t('comun.observaciones')} value={obs} onChange={(e) => setObs(e.target.value.slice(0, 500))} fullWidth multiline minRows={2}
              required={diferencias.length > 0} error={Boolean(errorObs)} helperText={errorObs ? t('errores.sinHtml') : diferencias.length ? t('cierre.obsDiferencias') : null} />
            <Box sx={{ mt: 2, textAlign: 'right' }}>
              <Button variant="contained" color="warning" disabled={Boolean(r.bloqueantes?.length) || errorObs || requiereObs} onClick={() => setDialogo('cerrar')}>{t('cierre.cerrarPeriodo')}</Button>
            </Box>
          </Paper>)}
        {cerrado && <Alert severity="info" sx={{ mb: 2 }}>{t('cierre.yaCerrado', { usuario: r.cierre.usuario, fecha: fechaHora(r.cierre.fechaCierre) })}</Alert>}
      </>)}
      <Paper>
        <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{t('cierre.historial')}</Typography>
        <TableContainer><Table size="small" aria-label={t('cierre.historial')}>
          <TableHead><TableRow>
            <TableCell>{t('cierre.periodo')}</TableCell><TableCell>{t('comun.estado')}</TableCell><TableCell>{t('cierre.fechaCierre')}</TableCell>
            <TableCell align="right">{t('cierre.ventas')}</TableCell><TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('comun.observaciones')}</TableCell><TableCell align="right">{t('comun.acciones')}</TableCell>
          </TableRow></TableHead>
          <TableBody>
            <TablaEstado columnas={6} cargando={historial.cargando && !historial.datos} error={historial.error} vacio={!historial.datos?.length} onReintentar={historial.recargar} />
            {(historial.datos || []).map((c) => (
              <TableRow key={c.id}>
                <TableCell>{c.periodo}</TableCell>
                <TableCell><EstadoChip estado={c.estado} />{c.motivoReapertura && <Typography variant="caption" component="div">{c.usuarioReapertura}: {c.motivoReapertura}</Typography>}</TableCell>
                <TableCell>{fechaHora(c.fechaCierre)} · {c.usuario}</TableCell>
                <TableCell align="right">{moneda(c.totalVentas)}</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{c.observaciones}</TableCell>
                <TableCell align="right">{c.estado === 'CERRADO' && <Button size="small" onClick={() => setDialogo({ periodo: c.periodo })}>{t('cierre.reabrir')}</Button>}</TableCell>
              </TableRow>))}
          </TableBody>
        </Table></TableContainer>
      </Paper>
      <ConfirmDialog abierto={dialogo === 'cerrar'} titulo={t('cierre.confirmarTitulo', { periodo })} mensaje={t('cierre.confirmarMensaje')} color="warning"
        textoConfirmar={t('cierre.cerrarPeriodo')} cargando={enviando} onConfirmar={cerrar} onCancelar={() => setDialogo(null)} />
      <MotivoDialog abierto={Boolean(dialogo?.periodo)} titulo={t('cierre.reabrirTitulo', { periodo: dialogo?.periodo })} mensaje={t('cierre.reabrirMensaje')}
        textoConfirmar={t('cierre.reabrir')} color="warning" onConfirmar={reabrir} onCancelar={() => setDialogo(null)} />
    </section>
  );
}
