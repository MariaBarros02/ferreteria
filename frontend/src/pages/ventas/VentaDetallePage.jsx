/*
 * Nombre: VentaDetallePage.jsx
 * Descripción: Detalle de una venta (RF-18): datos del documento, cliente, vendedor, líneas con presentación, descuentos, IVA, garantía y devoluciones, totales, factura, devoluciones asociadas, movimientos de inventario y eventos de auditoría.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Link as RouterLink, useParams } from 'react-router-dom';
import { Alert, Box, Button, Chip, CircularProgress, Divider, Grid, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ReceiptIcon from '@mui/icons-material/Receipt';
import AssignmentReturnIcon from '@mui/icons-material/AssignmentReturn';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import EstadoChip from '../../components/EstadoChip';
import PageHeader from '../../components/PageHeader';
import useCarga from '../../hooks/useCarga';
import { cantidad, fecha, fechaHora, moneda as fmtMoneda, porcentaje, tasaTexto } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { leyendaComprobante } from '../../utils/comprobante';

export function Dato({ etiqueta, children }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" component="div">{etiqueta}</Typography>
      <Typography variant="body2" component="div">{children ?? '—'}</Typography>
    </Box>
  );
}

export default function VentaDetallePage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const { rol } = useAuth();
  const { datos: d, cargando, error, recargar } = useCarga(() => api.ventas.detalle(id), [id]);

  const volver = <Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to="/ventas" sx={{ mb: 1 }}>{t('venta.volverListado')}</Button>;
  if (cargando && !d) return <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label={t('comun.cargando')} /></Box>;
  if (error) {
    return (<>{volver}<Alert severity="error" role="alert" action={<Button onClick={recargar}>{t('comun.reintentar')}</Button>}>{mensajeDeError(t, error)}</Alert></>);
  }
  const v = d.venta;
  const codigo = v.moneda || 'COP';
  const extranjera = codigo !== 'COP';
  /** Importe en la moneda de la venta. */
  const moneda = (n, decimales) => fmtMoneda(n, decimales, codigo);
  const devolvible = v.estado === 'ACTIVO' && v.lineas.some((l) => Number(l.cantidadDevuelta || 0) < Number(l.cantidad));

  return (
    <section aria-labelledby="venta-detalle-titulo">
      <PageHeader id="venta-detalle-titulo" volver={volver}
        titulo={t('venta.detalleTitulo', { numero: v.numeroInterno })}
        descripcion={<><EstadoChip estado={v.estado} /> {fechaHora(v.fecha)}</>}
        acciones={<>
          <Button variant="contained" startIcon={<ReceiptIcon />} component={RouterLink} to={`/ventas/${v.id}/comprobante`}>{t('venta.verComprobante')}</Button>
          {puede(rol, 'devoluciones.cliente') && devolvible && (
            <Button variant="outlined" startIcon={<AssignmentReturnIcon />} component={RouterLink} to={`/devoluciones/nueva?venta=${v.id}`}>{t('devolucion.registrar')}</Button>)}
        </>} />
      <Alert severity={v.factura?.validaFiscalmente ? 'success' : 'info'} sx={{ mb: 2 }}>{leyendaComprobante(t, v)}</Alert>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" component="h2" gutterBottom>{t('venta.documento')}</Typography>
            <Stack gap={1}>
              <Dato etiqueta={t('venta.numero')}>{v.numeroInterno}</Dato>
              <Dato etiqueta={t('venta.factura')}>{v.factura?.numeracionDian}</Dato>
              <Dato etiqueta={t('venta.modalidad')}>{v.factura ? t(`venta.modalidades.${v.factura.modalidad}`) : null}</Dato>
              <Dato etiqueta={t('venta.vendedor')}>{d.usuarioNombre ? `${d.usuarioNombre} (${d.usuarioCorreo})` : null}</Dato>
              <Dato etiqueta={t('comun.observaciones')}>{v.observaciones}</Dato>
            </Stack>
          </Paper>
        </Grid>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" component="h2" gutterBottom>{t('venta.cliente')}</Typography>
            <Stack gap={1}>
              <Dato etiqueta={t('tercero.nombre')}>{d.cliente?.nombre}</Dato>
              <Dato etiqueta={t('tercero.numeroIdentificacion')}>{d.cliente ? `${d.cliente.tipoIdentificacion} ${d.cliente.numeroIdentificacion}` : null}</Dato>
              <Dato etiqueta={t('tercero.direccion')}>{d.cliente ? `${d.cliente.direccion}, ${d.cliente.ciudad}` : null}</Dato>
              <Dato etiqueta={t('tercero.telefono')}>{d.cliente?.telefono}</Dato>
              <Dato etiqueta={t('tercero.correo')}>{d.cliente?.correo}</Dato>
            </Stack>
          </Paper>
        </Grid>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" component="h2" gutterBottom>{t('venta.resumen')}</Typography>
            <Stack gap={1}>
              <Dato etiqueta={t('moneda.moneda')}>{extranjera ? `${codigo} · ${tasaTexto(codigo, v.tasaCambio)}` : codigo}</Dato>
              <Dato etiqueta={t('venta.subtotal')}>{moneda(v.subtotalMoneda ?? v.subtotal, 2)}</Dato>
              <Dato etiqueta={t('venta.descuentos')}>{moneda(v.descuentosMoneda ?? v.totalDescuentos, 2)}</Dato>
              <Dato etiqueta={t('venta.impuestos')}>{moneda(v.impuestosMoneda ?? v.totalImpuestos, 2)}</Dato>
              <Divider />
              <Dato etiqueta={t('venta.total')}><strong>{moneda(v.totalMoneda ?? v.total, 2)}</strong></Dato>
              {extranjera && <Dato etiqueta={t('moneda.equivalenteCop')}>{fmtMoneda(v.total, 2)}</Dato>}
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      <Paper sx={{ mb: 2 }}>
        <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{t('venta.lineas')}</Typography>
        <TableContainer>
          <Table size="small" aria-label={t('venta.lineas')}>
            <TableHead><TableRow>
              <TableCell>{t('venta.producto')}</TableCell>
              <TableCell align="right">{t('venta.cantidad')}</TableCell>
              <TableCell align="right">{t('venta.precioUnitario')}</TableCell>
              <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('venta.descuentos')}</TableCell>
              <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('venta.iva')}</TableCell>
              <TableCell align="right">{t('venta.total')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('venta.garantia')}</TableCell>
              <TableCell align="right">{t('venta.devuelto')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {v.lineas.map((l) => (
                <TableRow key={l.id ?? l.productoId}>
                  <TableCell>{l.producto ?? `#${l.productoId}`}<Typography variant="caption" color="text.secondary" component="div">{l.codigoBarras}</Typography></TableCell>
                  <TableCell align="right">
                    {cantidad(l.cantidad)} {l.unidadBase}
                    {l.unidad && <Typography variant="caption" color="text.secondary" component="div">{cantidad(l.cantidadPresentacion)} {l.unidad}</Typography>}
                  </TableCell>
                  <TableCell align="right">{moneda(l.precioMoneda ?? l.precioAplicado, 2)}</TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{moneda(l.descuentoMoneda ?? l.descuento, 2)} ({porcentaje(l.porcentajeDescuento)})</TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{moneda(l.impuestoMoneda ?? l.impuesto, 2)} ({porcentaje(l.porcentajeIva)})</TableCell>
                  <TableCell align="right">{moneda(l.totalMoneda ?? l.total, 2)}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{l.garantiaHasta ? fecha(l.garantiaHasta) : '—'}</TableCell>
                  <TableCell align="right">{Number(l.cantidadDevuelta) > 0 ? <Chip size="small" color="warning" label={cantidad(l.cantidadDevuelta)} /> : '—'}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" component="h2" gutterBottom>{t('venta.devoluciones')}</Typography>
            {!d.devoluciones?.length ? <Typography color="text.secondary">{t('venta.sinDevoluciones')}</Typography> : (
              <Stack gap={1} component="ul" sx={{ pl: 0, listStyle: 'none', m: 0 }}>
                {d.devoluciones.map((x) => (
                  <li key={x.id}>
                    <RouterLink to={`/devoluciones/${x.id}`}>{x.numero}</RouterLink> · {fechaHora(x.fecha)} · {fmtMoneda(x.total, 2)} · {t(`devolucion.tratamientos.${x.tratamiento}`)}
                  </li>))}
              </Stack>)}
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" component="h2" gutterBottom>{t('venta.trazabilidad')}</Typography>
            <Stack gap={0.5} component="ul" sx={{ pl: 2, m: 0 }}>
              {(d.movimientos || []).map((m) => (
                <li key={`m${m.id}`}><Typography variant="body2">{fechaHora(m.fechaHora)} · {t(`movimiento.tipos.${m.tipo}`)} · #{m.idProducto} · {cantidad(m.cantidad)}{m.idLote ? ` · ${t('inventario.lote')} ${m.idLote}` : ''}</Typography></li>))}
              {(d.eventos || []).map((e) => (
                <li key={`e${e.id}`}><Typography variant="body2">{fechaHora(e.fechaHora)} · {e.accion} · {e.correoUsuario} · {e.descripcion}</Typography></li>))}
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </section>
  );
}
