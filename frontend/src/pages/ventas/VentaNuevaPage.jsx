/*
 * Nombre: VentaNuevaPage.jsx
 * Descripción: Registro de venta (RF-04, RF-11): selector de cliente con búsqueda, selector de productos con búsqueda y lector de código de barras, líneas editables con presentación, descuento y reserva, vista previa de importes (el servidor calcula el total definitivo), confirmación y clave de idempotencia.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useMemo, useRef, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Alert, Box, Button, Card, CardContent, Divider, Grid, IconButton, MenuItem, Paper, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import ConfirmDialog from '../../components/ConfirmDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import ProductoSelector from '../../components/ProductoSelector';
import TerceroSelector from '../../components/TerceroSelector';
import useCarga from '../../hooks/useCarga';
import useEnLinea from '../../hooks/useEnLinea';
import { nuevaClaveOperacion } from '../../utils/archivos';
import { cantidad as fmtCantidad, fecha, moneda, porcentaje } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { factor, linea as calcularLinea, totales as sumar } from '../../utils/totales';
import { numero, validarLineasVenta } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

/** Selector de reserva activa del cliente para el producto (opcional). */
function SelectorReserva({ cliente, producto, valor, onCambio, indice }) {
  const { t } = useTranslation();
  const { datos } = useCarga(() => (cliente && producto
    ? api.inventario.reservas({ estado: 'ACTIVA', clienteId: cliente.id, productoId: producto.id, tamano: 50 })
    : Promise.resolve({ contenido: [] })), [cliente?.id, producto?.id]);
  const reservas = datos?.contenido || [];
  if (!reservas.length) return null;
  return (
    <TextField select size="small" label={t('venta.reserva')} value={valor || ''} onChange={(e) => onCambio(e.target.value)}
      id={`linea-${indice}-reserva`} sx={{ minWidth: 160 }}>
      <MenuItem value="">{t('venta.sinReserva')}</MenuItem>
      {reservas.map((r) => <MenuItem key={r.id} value={r.id}>{`#${r.id} · ${fmtCantidad(r.cantidad)} · ${fecha(r.fechaLimite)}`}</MenuItem>)}
    </TextField>
  );
}

export default function VentaNuevaPage() {
  const { t } = useTranslation();
  const notificar = useNotify();
  const navegar = useNavigate();
  const enLinea = useEnLinea();
  const [cliente, setCliente] = useState(null);
  const [lineas, setLineas] = useState([]);
  const [observaciones, setObservaciones] = useState('');
  const [intentado, setIntentado] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [errorServidor, setErrorServidor] = useState(null);
  const claveOperacion = useRef(nuevaClaveOperacion());   // se conserva entre reintentos del mismo envío

  const agregar = (p) => {
    if (!p) return;
    setLineas((ls) => {
      const i = ls.findIndex((l) => l.producto.id === p.id && !l.unidad);
      if (i >= 0) {   // lector de código: el mismo producto suma una unidad
        const copia = [...ls];
        copia[i] = { ...copia[i], cantidad: String(numero(copia[i].cantidad) + 1) };
        return copia;
      }
      return [...ls, { producto: p, cantidad: '1', unidad: '', porcentajeDescuento: '', reservaId: '' }];
    });
  };
  const editar = (i, campo, v) => setLineas((ls) => ls.map((l, j) => (j === i ? { ...l, [campo]: v } : l)));
  const quitar = (i) => setLineas((ls) => ls.filter((_, j) => j !== i));

  const calculos = useMemo(() => lineas.map((l) => {
    const base = numero(l.cantidad) * factor(l.producto, l.unidad);
    return { base, ...calcularLinea(base, l.producto.precioVigente, numero(l.porcentajeDescuento), l.producto.porcentajeIva) };
  }), [lineas]);
  const total = useMemo(() => sumar(calculos), [calculos]);
  const validacion = validarLineasVenta(lineas);
  const errorObs = observaciones && !SIN_HTML.test(observaciones) ? 'errores.sinHtml' : null;
  const valido = Boolean(cliente) && validacion.valido && !errorObs;

  const revisar = () => {
    setIntentado(true);
    setErrorServidor(null);
    if (valido) setConfirmando(true);
  };

  const registrar = async () => {
    setEnviando(true);
    try {
      const venta = await api.ventas.crear({
        clienteId: cliente.id,
        idOperacion: claveOperacion.current,
        observaciones: observaciones.trim() || null,
        lineas: lineas.map((l) => ({
          productoId: l.producto.id, cantidad: numero(l.cantidad), ...(l.unidad ? { unidad: l.unidad } : {}),
          porcentajeDescuento: numero(l.porcentajeDescuento), ...(l.reservaId ? { reservaId: Number(l.reservaId) } : {}),
        })),
      });
      claveOperacion.current = nuevaClaveOperacion();
      notificar(t('venta.creada', { numero: venta.numeroInterno }));
      navegar(`/ventas/${venta.id}`);
    } catch (e) {
      setConfirmando(false);
      setErrorServidor(mensajeDeError(t, e));
    } finally {
      setEnviando(false);
    }
  };

  const errorDe = (i, campo) => (intentado && validacion.porLinea[i]?.[campo] ? t(validacion.porLinea[i][campo]) : null);

  return (
    <section aria-labelledby="venta-nueva-titulo">
      <PageHeader id="venta-nueva-titulo" titulo={t('venta.nueva')} descripcion={t('venta.nuevaDescripcion')}
        volver={<Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to="/ventas" sx={{ mb: 1 }}>{t('venta.volverListado')}</Button>} />
      {!enLinea && <Alert severity="warning" sx={{ mb: 2 }}>{t('venta.sinConexionNoRegistra')}</Alert>}
      {errorServidor && <Alert severity="error" role="alert" sx={{ mb: 2 }} onClose={() => setErrorServidor(null)}>{errorServidor}</Alert>}
      <Grid container spacing={2}>
        <Grid item xs={12} lg={8}>
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" component="h2" gutterBottom>{t('venta.cliente')}</Typography>
            <TerceroSelector id="venta-cliente" rol="cliente" valor={cliente} onCambio={setCliente} requerido
              error={intentado && !cliente ? t('errores.obligatorio') : null} autoFocus />
            {cliente && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                {cliente.direccion} · {cliente.ciudad} · {cliente.telefono} · {cliente.correo}
              </Typography>)}
          </Paper>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" component="h2" gutterBottom>{t('venta.lineas')}</Typography>
            <ProductoSelector id="venta-producto" valor={null} onCambio={agregar} limpiarAlSeleccionar
              textoAyuda={t('selector.lectorAyuda')} />
            {intentado && validacion.sinLineas && <Typography color="error" role="alert" sx={{ mt: 1 }}>{t('errores.lineas')}</Typography>}
            {lineas.length > 0 && (
              <TableContainer sx={{ mt: 2 }}>
                <Table size="small" aria-label={t('venta.lineas')}>
                  <TableHead><TableRow>
                    <TableCell>{t('venta.producto')}</TableCell>
                    <TableCell sx={{ minWidth: 110 }}>{t('venta.cantidad')}</TableCell>
                    <TableCell sx={{ minWidth: 130 }}>{t('venta.presentacion')}</TableCell>
                    <TableCell sx={{ minWidth: 100 }}>{t('venta.descuentoPct')}</TableCell>
                    <TableCell align="right">{t('venta.precioUnitario')}</TableCell>
                    <TableCell align="right">{t('venta.total')}</TableCell>
                    <TableCell><span className="visualmente-oculto">{t('comun.acciones')}</span></TableCell>
                  </TableRow></TableHead>
                  <TableBody>
                    {lineas.map((l, i) => {
                      const c = calculos[i];
                      const excede = c.base > Number(l.producto.disponible ?? Infinity) && !l.reservaId;
                      return (
                        <TableRow key={`${l.producto.id}-${i}`}>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>{l.producto.nombre} {l.producto.marca} {l.producto.presentacion}</Typography>
                            <Typography variant="caption" color="text.secondary" component="div">
                              {l.producto.codigoBarras} · {t('selector.disponible', { cantidad: fmtCantidad(l.producto.disponible), unidad: l.producto.unidadBase })} · {t('venta.iva')} {porcentaje(l.producto.porcentajeIva)}
                            </Typography>
                            {errorDe(i, 'producto') && <Typography variant="caption" color="error" role="alert">{errorDe(i, 'producto')}</Typography>}
                            {excede && <Typography variant="caption" color="warning.main" component="div">{t('venta.excedeDisponible')}</Typography>}
                            <SelectorReserva cliente={cliente} producto={l.producto} valor={l.reservaId} indice={i}
                              onCambio={(v) => editar(i, 'reservaId', v)} />
                          </TableCell>
                          <TableCell>
                            <TextField size="small" value={l.cantidad} onChange={(e) => editar(i, 'cantidad', e.target.value)}
                              inputProps={{ inputMode: 'decimal', 'aria-label': `${t('venta.cantidad')} ${i + 1}` }}
                              error={Boolean(errorDe(i, 'cantidad'))} helperText={errorDe(i, 'cantidad')} />
                          </TableCell>
                          <TableCell>
                            <TextField select size="small" value={l.unidad} onChange={(e) => editar(i, 'unidad', e.target.value)}
                              inputProps={{ 'aria-label': `${t('venta.presentacion')} ${i + 1}` }} fullWidth>
                              <MenuItem value="">{l.producto.unidadBase}</MenuItem>
                              {(l.producto.unidades || []).map((u) => (
                                <MenuItem key={u.unidadPresentacion} value={u.unidadPresentacion}>{`${u.unidadPresentacion} (×${fmtCantidad(u.factor)})`}</MenuItem>))}
                            </TextField>
                          </TableCell>
                          <TableCell>
                            <TextField size="small" value={l.porcentajeDescuento} placeholder="0"
                              onChange={(e) => editar(i, 'porcentajeDescuento', e.target.value)}
                              inputProps={{ inputMode: 'decimal', 'aria-label': `${t('venta.descuentoPct')} ${i + 1}` }}
                              error={Boolean(errorDe(i, 'porcentajeDescuento'))} helperText={errorDe(i, 'porcentajeDescuento')} />
                          </TableCell>
                          <TableCell align="right">{moneda(l.producto.precioVigente)}</TableCell>
                          <TableCell align="right">{moneda(c.total)}</TableCell>
                          <TableCell>
                            <IconButton aria-label={`${t('venta.quitarLinea')} ${i + 1}`} onClick={() => quitar(i)}><DeleteIcon /></IconButton>
                          </TableCell>
                        </TableRow>);
                    })}
                  </TableBody>
                </Table>
              </TableContainer>)}
            <TextField label={t('comun.observaciones')} value={observaciones} onChange={(e) => setObservaciones(e.target.value.slice(0, 255))}
              fullWidth multiline minRows={2} margin="normal" error={Boolean(errorObs)} helperText={errorObs ? t(errorObs) : `${observaciones.length}/255`} />
          </Paper>
        </Grid>
        <Grid item xs={12} lg={4}>
          <Card sx={{ position: { lg: 'sticky' }, top: { lg: 88 } }} aria-labelledby="venta-resumen">
            <CardContent>
              <Typography id="venta-resumen" variant="h6" component="h2" gutterBottom>{t('venta.resumen')}</Typography>
              <Stack gap={0.5}>
                <Fila etiqueta={t('venta.subtotal')} valor={moneda(total.subtotal, 2)} />
                <Fila etiqueta={t('venta.descuentos')} valor={`− ${moneda(total.descuentos, 2)}`} />
                <Fila etiqueta={t('venta.impuestos')} valor={moneda(total.impuestos, 2)} />
                <Divider sx={{ my: 1 }} />
                <Fila etiqueta={t('venta.total')} valor={moneda(total.total, 2)} fuerte />
              </Stack>
              <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1 }}>{t('venta.previsualizacionAviso')}</Typography>
              <Button fullWidth variant="contained" size="large" sx={{ mt: 2 }} onClick={revisar} disabled={!enLinea || enviando}>
                {t('venta.registrar')}
              </Button>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <ConfirmDialog abierto={confirmando} titulo={t('venta.confirmarTitulo')} color="primary" textoConfirmar={t('venta.confirmarRegistrar')}
        cargando={enviando} onCancelar={() => setConfirmando(false)} onConfirmar={registrar}
        mensaje={t('venta.confirmarMensaje', { cliente: cliente?.nombre ?? '', lineas: lineas.length, total: moneda(total.total, 2) })} />
    </section>
  );
}

function Fila({ etiqueta, valor, fuerte }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
      <Typography variant={fuerte ? 'subtitle1' : 'body2'} sx={{ fontWeight: fuerte ? 700 : 400 }}>{etiqueta}</Typography>
      <Typography variant={fuerte ? 'subtitle1' : 'body2'} sx={{ fontWeight: fuerte ? 700 : 400 }} data-testid={fuerte ? 'venta-total' : undefined}>{valor}</Typography>
    </Box>
  );
}
