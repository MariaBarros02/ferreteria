/*
 * Nombre: DevolucionDetallePage.jsx
 * Descripción: Detalle de una devolución (RF-13): documento de origen, tercero, motivo, tratamiento, líneas con estado del producto, origen y lote.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Link as RouterLink, useParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Grid, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import EstadoChip from '../../components/EstadoChip';
import PageHeader from '../../components/PageHeader';
import useCarga from '../../hooks/useCarga';
import { cantidad, fechaHora, moneda } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { Dato } from '../ventas/VentaDetallePage';

export default function DevolucionDetallePage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const { datos: d, cargando, error, recargar } = useCarga(() => api.devoluciones.obtener(id), [id]);
  const volver = <Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to="/devoluciones" sx={{ mb: 1 }}>{t('devolucion.volver')}</Button>;
  if (cargando && !d) return <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label={t('comun.cargando')} /></Box>;
  if (error) return (<>{volver}<Alert severity="error" role="alert" action={<Button onClick={recargar}>{t('comun.reintentar')}</Button>}>{mensajeDeError(t, error)}</Alert></>);
  const origen = d.ventaId ? <RouterLink to={`/ventas/${d.ventaId}`}>{d.ventaNumero}</RouterLink> : <RouterLink to={`/compras/${d.compraId}`}>{d.compraNumero}</RouterLink>;
  return (
    <section aria-labelledby="devolucion-titulo">
      <PageHeader id="devolucion-titulo" volver={volver} titulo={t('devolucion.detalleTitulo', { numero: d.numero })}
        descripcion={<><EstadoChip estado={d.estado} /> {t(`devolucion.tipos.${d.tipo}`)} · {fechaHora(d.fecha)}</>} />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12} md={6}><Paper sx={{ p: 2 }}><Stack gap={1}>
          <Dato etiqueta={t('devolucion.documentoOrigen')}>{origen}</Dato>
          <Dato etiqueta={t('devolucion.tercero')}>{d.tercero}</Dato>
          <Dato etiqueta={t('compra.usuario')}>{d.usuario}</Dato>
        </Stack></Paper></Grid>
        <Grid item xs={12} md={6}><Paper sx={{ p: 2 }}><Stack gap={1}>
          <Dato etiqueta={t('comun.motivo')}>{d.motivo}</Dato>
          <Dato etiqueta={t('devolucion.tratamiento')}>{t(`devolucion.tratamientos.${d.tratamiento}`)}</Dato>
          <Dato etiqueta={t('comun.observaciones')}>{d.observaciones}</Dato>
          <Dato etiqueta={t('venta.total')}><strong>{moneda(d.total, 2)}</strong></Dato>
        </Stack></Paper></Grid>
      </Grid>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('devolucion.lineas')}>
            <TableHead><TableRow>
              <TableCell>{t('venta.producto')}</TableCell><TableCell align="right">{t('venta.cantidad')}</TableCell>
              <TableCell>{d.tipo === 'CLIENTE' ? t('devolucion.estadoProducto') : t('devolucion.origen')}</TableCell>
              <TableCell>{t('inventario.lote')}</TableCell><TableCell align="right">{t('devolucion.valor')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {d.lineas.map((l) => (
                <TableRow key={l.id}>
                  <TableCell>{l.producto}</TableCell><TableCell align="right">{cantidad(l.cantidad)}</TableCell>
                  <TableCell>{l.estadoProducto ? t(`devolucion.estados.${l.estadoProducto}`) : l.origen ? t(`inventario.estados.${l.origen}`) : '—'}</TableCell>
                  <TableCell>{l.lote || '—'}</TableCell><TableCell align="right">{moneda(l.valor, 2)}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
      <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1 }}>{t('devolucion.inmutable')}</Typography>
    </section>
  );
}
