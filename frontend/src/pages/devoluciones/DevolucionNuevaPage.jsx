/*
 * Nombre: DevolucionNuevaPage.jsx
 * Descripción: Registro de devolución de cliente (RF-13): búsqueda de la venta de origen, cantidades por línea limitadas a lo vendido menos lo ya devuelto, estado del producto (apto vuelve a disponible; dañado o vencido no se revende), motivo y tratamiento, con confirmación e idempotencia.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, CircularProgress, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import ConfirmDialog from '../../components/ConfirmDialog';
import EntitySelector from '../../components/EntitySelector';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import { nuevaClaveOperacion } from '../../utils/archivos';
import { cantidad, fechaHora, moneda } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { errorCantidad, numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

/** Cantidad aún devolvible de una línea de venta. */
export const devolvible = (l) => Math.max(0, Number(l.cantidad) - Number(l.cantidadDevuelta || 0));

export default function DevolucionNuevaPage() {
  const { t } = useTranslation();
  const notificar = useNotify();
  const navegar = useNavigate();
  const [params] = useSearchParams();
  const [venta, setVenta] = useState(null);
  const [cargandoVenta, setCargandoVenta] = useState(false);
  const [filas, setFilas] = useState({});
  const [motivo, setMotivo] = useState('');
  const [tratamiento, setTratamiento] = useState('REEMBOLSO');
  const [obs, setObs] = useState('');
  const [intentado, setIntentado] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  const clave = useRef(nuevaClaveOperacion());

  const cargarVenta = async (id) => {
    if (!id) { setVenta(null); return; }
    setCargandoVenta(true); setFalla(null);
    try {
      const v = (await api.ventas.detalle(id)).venta;   // el detalle incluye lo ya devuelto por línea
      setVenta(v);
      setFilas(Object.fromEntries(v.lineas.map((l) => [l.id, { cantidad: '', estadoProducto: 'APTO' }])));
    } catch (e) { setFalla(mensajeDeError(t, e)); }
    finally { setCargandoVenta(false); }
  };
  useEffect(() => { const id = params.get('venta'); if (id) cargarVenta(id); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const buscarVentas = async (q) => (await api.ventas.listar({ q, estado: 'ACTIVO', tamano: 15 })).contenido;
  const editar = (id, c, v) => setFilas((f) => ({ ...f, [id]: { ...f[id], [c]: v } }));
  const lineas = venta?.lineas || [];
  const elegidas = lineas.filter((l) => String(filas[l.id]?.cantidad || '').trim());
  const errores = {};
  elegidas.forEach((l) => {
    const c = errorCantidad(filas[l.id].cantidad);
    if (c) errores[l.id] = t(c);
    else if (numero(filas[l.id].cantidad) > devolvible(l)) errores[l.id] = t('devolucion.excedeVendido', { maximo: cantidad(devolvible(l)) });
  });
  const errorMotivo = !motivo.trim() ? t('errores.obligatorio') : !SIN_HTML.test(motivo) ? t('errores.sinHtml') : null;
  const errorObs = obs && !SIN_HTML.test(obs) ? t('errores.sinHtml') : null;
  const valorEstimado = elegidas.reduce((s, l) => s + (Number(l.total) / Number(l.cantidad)) * numero(filas[l.id].cantidad), 0);

  const revisar = () => {
    setIntentado(true);
    if (!venta || !elegidas.length || Object.keys(errores).length || errorMotivo || errorObs) return;
    setConfirmando(true);
  };
  const registrar = async () => {
    setEnviando(true); setFalla(null);
    try {
      const d = await api.devoluciones.cliente({
        ventaId: venta.id, motivo: motivo.trim(), tratamiento, observaciones: obs.trim() || null, idOperacion: clave.current,
        lineas: elegidas.map((l) => ({ idLineaVenta: l.id, cantidad: numero(filas[l.id].cantidad), estadoProducto: filas[l.id].estadoProducto })),
      });
      clave.current = nuevaClaveOperacion();
      notificar(t('devolucion.creada', { numero: d.numero }));
      navegar(`/devoluciones/${d.id}`);
    } catch (e) { setConfirmando(false); setFalla(mensajeDeError(t, e)); }
    finally { setEnviando(false); }
  };

  return (
    <section aria-labelledby="devolucion-nueva-titulo">
      <PageHeader id="devolucion-nueva-titulo" titulo={t('devolucion.nuevaCliente')} descripcion={t('devolucion.nuevaDescripcion')}
        volver={<Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to="/devoluciones" sx={{ mb: 1 }}>{t('devolucion.volver')}</Button>} />
      {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }} onClose={() => setFalla(null)}>{falla}</Alert>}
      <Paper sx={{ p: 2, mb: 2 }}>
        <EntitySelector id="devolucion-venta" label={t('devolucion.ventaOrigen')} valor={venta ? { id: venta.id, numeroInterno: venta.numeroInterno, cliente: venta.cliente, fecha: venta.fecha, total: venta.total } : null}
          onCambio={(v) => cargarVenta(v?.id)} buscar={buscarVentas} requerido autoFocus={!params.get('venta')}
          etiqueta={(v) => `${v.numeroInterno} · ${v.cliente ?? ''}`} placeholder={t('devolucion.buscarVenta')}
          error={intentado && !venta ? t('errores.obligatorio') : null}
          renderOpcion={(props, v) => <li {...props}>{v.numeroInterno} · {v.cliente} · {fechaHora(v.fecha)} · {moneda(v.total)}</li>} />
        {cargandoVenta && <CircularProgress size={24} aria-label={t('comun.cargando')} />}
      </Paper>
      {venta && (
        <Paper sx={{ p: 2 }}>
          {venta.estado !== 'ACTIVO' && <Alert severity="warning" sx={{ mb: 2 }}>{t('devolucion.ventaAnulada')}</Alert>}
          <TableContainer>
            <Table size="small" aria-label={t('venta.lineas')}>
              <TableHead><TableRow>
                <TableCell>{t('venta.producto')}</TableCell><TableCell align="right">{t('devolucion.vendido')}</TableCell>
                <TableCell align="right">{t('devolucion.yaDevuelto')}</TableCell><TableCell>{t('devolucion.cantidadDevolver')}</TableCell>
                <TableCell>{t('devolucion.estadoProducto')}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {lineas.map((l) => {
                  const f = filas[l.id] || {};
                  const max = devolvible(l);
                  return (
                    <TableRow key={l.id}>
                      <TableCell>{l.producto}<Typography variant="caption" component="div" color="text.secondary">{moneda(l.total, 2)}</Typography></TableCell>
                      <TableCell align="right">{cantidad(l.cantidad)} {l.unidadBase}</TableCell>
                      <TableCell align="right">{cantidad(l.cantidadDevuelta || 0)}</TableCell>
                      <TableCell sx={{ minWidth: 130 }}>
                        <TextField size="small" value={f.cantidad ?? ''} onChange={(e) => editar(l.id, 'cantidad', e.target.value)} disabled={max <= 0}
                          inputProps={{ inputMode: 'decimal', 'aria-label': `${t('devolucion.cantidadDevolver')} ${l.producto}` }}
                          error={Boolean(errores[l.id])} helperText={errores[l.id] || (max > 0 ? t('devolucion.maximo', { maximo: cantidad(max) }) : t('devolucion.nadaPorDevolver'))} />
                      </TableCell>
                      <TableCell>
                        <TextField select size="small" value={f.estadoProducto ?? 'APTO'} onChange={(e) => editar(l.id, 'estadoProducto', e.target.value)} disabled={max <= 0}
                          inputProps={{ 'aria-label': `${t('devolucion.estadoProducto')} ${l.producto}` }}>
                          {['APTO', 'DANADO', 'VENCIDO'].map((x) => <MenuItem key={x} value={x}>{t(`devolucion.estados.${x}`)}</MenuItem>)}
                        </TextField>
                      </TableCell>
                    </TableRow>);
                })}
              </TableBody>
            </Table>
          </TableContainer>
          {intentado && !elegidas.length && <Typography color="error" role="alert" sx={{ mt: 1 }}>{t('compra.indiqueCantidad')}</Typography>}
          <Stack direction={{ xs: 'column', md: 'row' }} gap={2} mt={2}>
            <TextField label={t('comun.motivo')} value={motivo} onChange={(e) => setMotivo(e.target.value.slice(0, 255))} required fullWidth
              error={intentado && Boolean(errorMotivo)} helperText={intentado ? errorMotivo : null} />
            <TextField select label={t('devolucion.tratamiento')} value={tratamiento} onChange={(e) => setTratamiento(e.target.value)} sx={{ minWidth: 220 }}>
              {['REEMBOLSO', 'SALDO_FAVOR', 'CAMBIO', 'NINGUNO'].map((x) => <MenuItem key={x} value={x}>{t(`devolucion.tratamientos.${x}`)}</MenuItem>)}
            </TextField>
          </Stack>
          <TextField label={t('comun.observaciones')} value={obs} onChange={(e) => setObs(e.target.value.slice(0, 255))} fullWidth multiline margin="normal"
            error={Boolean(errorObs)} helperText={errorObs} />
          <Stack direction="row" justifyContent="space-between" alignItems="center" gap={2}>
            <Typography>{t('devolucion.valorEstimado')}: <strong>{moneda(valorEstimado, 2)}</strong></Typography>
            <Button variant="contained" onClick={revisar} disabled={enviando || venta.estado !== 'ACTIVO'}>{t('devolucion.registrar')}</Button>
          </Stack>
        </Paper>)}
      <ConfirmDialog abierto={confirmando} titulo={t('devolucion.confirmarTitulo')} color="warning" textoConfirmar={t('devolucion.registrar')} cargando={enviando}
        mensaje={t('devolucion.confirmarMensaje', { venta: venta?.numeroInterno, lineas: elegidas.length, valor: moneda(valorEstimado, 2) })}
        onCancelar={() => setConfirmando(false)} onConfirmar={registrar} />
    </section>
  );
}
