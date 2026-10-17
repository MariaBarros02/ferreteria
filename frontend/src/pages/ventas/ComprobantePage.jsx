/*
 * Nombre: ComprobantePage.jsx
 * Descripción: Previsualización, impresión y exportación a PDF del comprobante de una venta (RF-19). La exportación se bloquea con los motivos del servidor si el documento está incompleto o anulado; la leyenda distingue el comprobante interno de la factura electrónica.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Divider, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PrintIcon from '@mui/icons-material/Print';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useNotify } from '../../components/Notifier';
import useCarga from '../../hooks/useCarga';
import { cantidad, fecha, fechaHora, moneda as fmtMoneda, tasaTexto } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { leyendaComprobante, nombreArchivoComprobante, tituloComprobante, unir } from '../../utils/comprobante';

export default function ComprobantePage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const notificar = useNotify();
  const [generando, setGenerando] = useState(false);
  const { datos: d, cargando, error, recargar } = useCarga(() => api.ventas.detalle(id), [id]);

  const volver = <Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to={`/ventas/${id}`} className="no-imprimir">{t('venta.volverDetalle')}</Button>;
  if (cargando && !d) return <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label={t('comun.cargando')} /></Box>;
  if (error) return (<>{volver}<Alert severity="error" role="alert" action={<Button onClick={recargar}>{t('comun.reintentar')}</Button>}>{mensajeDeError(t, error)}</Alert></>);

  const { venta: v, cliente, empresa } = d;
  const codigo = v.moneda || 'COP';
  const extranjera = codigo !== 'COP';
  const moneda = (n, decimales) => fmtMoneda(n, decimales, codigo);
  const pdf = async () => {
    setGenerando(true);
    try {
      const { crearComprobantePdf } = await import('../../pdf/comprobante');   // la librería de PDF se carga solo al usarla
      crearComprobantePdf(d, t).save(nombreArchivoComprobante(v));
    }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setGenerando(false); }
  };

  return (
    <section aria-labelledby="comprobante-titulo">
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} mb={2} className="no-imprimir">
        {volver}
        <Stack direction="row" gap={1}>
          <Button variant="outlined" startIcon={<PrintIcon />} onClick={() => window.print()} disabled={!d.exportable}>{t('comprobante.imprimir')}</Button>
          <Button variant="contained" startIcon={generando ? <CircularProgress size={16} color="inherit" /> : <PictureAsPdfIcon />}
            onClick={pdf} disabled={!d.exportable || generando}>{t('comprobante.descargarPdf')}</Button>
        </Stack>
      </Stack>
      {!d.exportable && (
        <Alert severity="warning" role="alert" sx={{ mb: 2 }} className="no-imprimir">
          {t('comprobante.noExportable')}
          <ul>{(d.motivosNoExportable || []).map((m) => <li key={m}>{m}</li>)}</ul>
        </Alert>)}
      <Paper className="comprobante" sx={{ p: { xs: 2, sm: 4 }, maxWidth: 900, mx: 'auto' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2}>
          <Box>
            <Typography variant="h6" component="p" sx={{ fontWeight: 700 }}>{empresa?.nombre}</Typography>
            {empresa?.nit && <Typography variant="body2">NIT {empresa.nit}</Typography>}
            {unir(empresa?.direccion, empresa?.ciudad) && <Typography variant="body2">{unir(empresa?.direccion, empresa?.ciudad)}</Typography>}
            {unir(empresa?.telefono, empresa?.correo) && <Typography variant="body2">{unir(empresa?.telefono, empresa?.correo)}</Typography>}
          </Box>
          <Box sx={{ textAlign: { sm: 'right' } }}>
            <Typography id="comprobante-titulo" variant="h6" component="h1" sx={{ fontWeight: 700 }}>{tituloComprobante(t, v)}</Typography>
            <Typography variant="body2">{t('comprobante.numeroInterno')}: {v.numeroInterno}</Typography>
            <Typography variant="body2">{t('comprobante.numeracion')}: {v.factura?.numeracionDian ?? '—'}</Typography>
            <Typography variant="body2">{t('comprobante.fecha')}: {fechaHora(v.factura?.fechaEmision || v.fecha)}</Typography>
            <Typography variant="body2">{t('moneda.moneda')}: {codigo}{extranjera ? ` · ${tasaTexto(codigo, v.tasaCambio)}` : ''}</Typography>
          </Box>
        </Stack>
        <Divider sx={{ my: 2 }} />
        <Typography variant="subtitle2" component="h2">{t('comprobante.cliente')}</Typography>
        <Typography variant="body2">{cliente?.nombre}</Typography>
        <Typography variant="body2">{unir(cliente?.tipoIdentificacion, cliente?.numeroIdentificacion)}</Typography>
        <Typography variant="body2">{unir(cliente?.direccion, cliente?.ciudad, cliente?.telefono, cliente?.correo)}</Typography>
        <Box sx={{ overflowX: 'auto', mt: 2 }}>
          <Table size="small" aria-label={t('venta.lineas')}>
            <TableHead><TableRow>
              <TableCell>{t('comprobante.descripcion')}</TableCell>
              <TableCell align="right">{t('comprobante.cantidad')}</TableCell>
              <TableCell align="right">{t('comprobante.precio')}</TableCell>
              <TableCell align="right">{t('comprobante.descuento')}</TableCell>
              <TableCell align="right">{t('comprobante.iva')}</TableCell>
              <TableCell align="right">{t('comprobante.total')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {v.lineas.map((l) => (
                <TableRow key={l.id ?? l.productoId}>
                  <TableCell>{l.producto ?? `#${l.productoId}`} <Typography variant="caption" color="text.secondary">({l.codigoBarras})</Typography>
                    {l.garantiaHasta && <Typography variant="caption" component="div">{t('comprobante.garantiaHasta')}: {fecha(l.garantiaHasta)}</Typography>}
                  </TableCell>
                  <TableCell align="right">{l.unidad ? `${cantidad(l.cantidadPresentacion)} ${l.unidad}` : `${cantidad(l.cantidad)} ${l.unidadBase ?? ''}`}</TableCell>
                  <TableCell align="right">{moneda(l.precioMoneda ?? l.precioAplicado, 2)}</TableCell>
                  <TableCell align="right">{moneda(l.descuentoMoneda ?? l.descuento, 2)}</TableCell>
                  <TableCell align="right">{moneda(l.impuestoMoneda ?? l.impuesto, 2)}</TableCell>
                  <TableCell align="right">{moneda(l.totalMoneda ?? l.total, 2)}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </Box>
        <Stack alignItems="flex-end" sx={{ mt: 2 }} gap={0.5}>
          <Typography variant="body2">{t('comprobante.subtotal')}: {moneda(v.subtotalMoneda ?? v.subtotal, 2)}</Typography>
          <Typography variant="body2">{t('comprobante.descuentos')}: {moneda(v.descuentosMoneda ?? v.totalDescuentos, 2)}</Typography>
          <Typography variant="body2">{t('comprobante.impuestos')}: {moneda(v.impuestosMoneda ?? v.totalImpuestos, 2)}</Typography>
          <Typography variant="h6" component="p" sx={{ fontWeight: 700 }} data-testid="comprobante-total">{t('comprobante.total')}: {moneda(v.totalMoneda ?? v.total, 2)}</Typography>
          {extranjera && <Typography variant="body2" data-testid="comprobante-equivalente">{t('moneda.equivalenteCop')}: {fmtMoneda(v.total, 2)}</Typography>}
        </Stack>
        {v.observaciones && <Typography variant="body2" sx={{ mt: 1 }}>{t('comprobante.observaciones')}: {v.observaciones}</Typography>}
        <Typography variant="body2" sx={{ mt: 1 }}>{t('comprobante.atendio')}: {d.usuarioNombre ?? '—'}</Typography>
        <Divider sx={{ my: 2 }} />
        <Typography variant="caption" component="p" sx={{ fontStyle: 'italic' }} data-testid="leyenda-comprobante">{leyendaComprobante(t, v)}</Typography>
      </Paper>
    </section>
  );
}
