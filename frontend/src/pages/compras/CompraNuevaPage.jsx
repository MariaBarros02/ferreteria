/*
 * Nombre: CompraNuevaPage.jsx
 * Descripción: Registro de orden de compra (RF-03, RF-23): proveedor con búsqueda, moneda del proveedor con tasa vigente, productos con búsqueda/lector de código, cantidad y costo por presentación en esa moneda, IVA y descuento por línea, vista previa de importes, confirmación e idempotencia. La mercancía entra al inventario solo al recibirla.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
import { useMemo, useRef, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Alert, Button, IconButton, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteIcon from '@mui/icons-material/Delete';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import ConfirmDialog from '../../components/ConfirmDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import ProductoSelector from '../../components/ProductoSelector';
import TerceroSelector from '../../components/TerceroSelector';
import SelectorMoneda, { COP } from '../../components/SelectorMoneda';
import useEnLinea from '../../hooks/useEnLinea';
import { nuevaClaveOperacion } from '../../utils/archivos';
import { cantidad as fmtCantidad, fecha, moneda as fmtMoneda, tasaTexto } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { enCop, linea as calcularLinea, totales as sumar } from '../../utils/totales';
import { errorCantidad, errorImporte, errorPorcentaje, numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

export default function CompraNuevaPage() {
  const { t } = useTranslation();
  const notificar = useNotify();
  const navegar = useNavigate();
  const enLinea = useEnLinea();
  const [proveedor, setProveedor] = useState(null);
  const [monedaDoc, setMonedaDoc] = useState(COP);
  const tasa = monedaDoc.funcional ? 1 : Number(monedaDoc.tasaVigente) || 1;
  const moneda = (n, d) => fmtMoneda(n, d, monedaDoc.codigo);
  const [lineas, setLineas] = useState([]);
  const [observaciones, setObservaciones] = useState('');
  const [intentado, setIntentado] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [errorServidor, setErrorServidor] = useState(null);
  const clave = useRef(nuevaClaveOperacion());

  const agregar = (p) => {
    if (!p) return;
    setLineas((ls) => (ls.some((l) => l.producto.id === p.id) ? ls
      : [...ls, { producto: p, cantidad: '1', unidad: '', costoUnitario: '', porcentajeIva: String(p.porcentajeIva ?? 0), porcentajeDescuento: '' }]));
  };
  const editar = (i, campo, v) => setLineas((ls) => ls.map((l, j) => (j === i ? { ...l, [campo]: v } : l)));
  const errores = lineas.map((l) => ({
    cantidad: errorCantidad(l.cantidad), costoUnitario: errorImporte(l.costoUnitario),
    porcentajeIva: errorPorcentaje(l.porcentajeIva), porcentajeDescuento: errorPorcentaje(l.porcentajeDescuento),
  }));
  const calculos = useMemo(() => lineas.map((l) => calcularLinea(numero(l.cantidad), numero(l.costoUnitario),
    numero(l.porcentajeDescuento), numero(l.porcentajeIva))), [lineas]);
  const total = sumar(calculos);
  const errorObs = observaciones && !SIN_HTML.test(observaciones);
  const valido = proveedor && lineas.length && errores.every((e) => Object.values(e).every((x) => !x)) && !errorObs;
  const err = (i, c) => (intentado && errores[i]?.[c] ? t(errores[i][c]) : null);

  const registrar = async () => {
    setEnviando(true);
    try {
      const c = await api.compras.crear({
        proveedorId: proveedor.id, moneda: monedaDoc.codigo, observaciones: observaciones.trim() || null, idOperacion: clave.current,
        lineas: lineas.map((l) => ({ productoId: l.producto.id, cantidad: numero(l.cantidad), ...(l.unidad ? { unidad: l.unidad } : {}),
          costoUnitario: numero(l.costoUnitario), porcentajeIva: numero(l.porcentajeIva), porcentajeDescuento: numero(l.porcentajeDescuento) })),
      });
      clave.current = nuevaClaveOperacion();
      notificar(t('compra.creada', { numero: c.numero }));
      navegar(`/compras/${c.id}`);
    } catch (e) { setConfirmando(false); setErrorServidor(mensajeDeError(t, e)); }
    finally { setEnviando(false); }
  };

  return (
    <section aria-labelledby="compra-nueva-titulo">
      <PageHeader id="compra-nueva-titulo" titulo={t('compra.nueva')} descripcion={t('compra.nuevaDescripcion')}
        volver={<Button size="small" startIcon={<ArrowBackIcon />} component={RouterLink} to="/compras" sx={{ mb: 1 }}>{t('compra.volverListado')}</Button>} />
      {errorServidor && <Alert severity="error" role="alert" sx={{ mb: 2 }} onClose={() => setErrorServidor(null)}>{errorServidor}</Alert>}
      <Paper sx={{ p: 2, mb: 2 }}>
        <TerceroSelector id="compra-proveedor" rol="proveedor" valor={proveedor} onCambio={setProveedor} requerido autoFocus
          error={intentado && !proveedor ? t('errores.obligatorio') : null} />
        <SelectorMoneda id="compra-moneda" valor={monedaDoc} onCambio={setMonedaDoc} label={t('moneda.monedaProveedor')} />
        <ProductoSelector id="compra-producto" valor={null} onCambio={agregar} limpiarAlSeleccionar mostrarPrecio={false}
          textoAyuda={t('selector.lectorAyuda')} />
        {intentado && !lineas.length && <Typography color="error" role="alert">{t('errores.lineas')}</Typography>}
        {lineas.length > 0 && (
         <TableContainer sx={{ mt: 2, position: 'relative' }}>
            <Table size="small" aria-label={t('compra.lineas')}>
              <TableHead><TableRow>
                <TableCell>{t('venta.producto')}</TableCell>
                <TableCell sx={{ minWidth: 100 }}>{t('venta.cantidad')}</TableCell>
                <TableCell sx={{ minWidth: 130 }}>{t('venta.presentacion')}</TableCell>
                <TableCell sx={{ minWidth: 130 }}>{t('compra.costoUnitario')} ({monedaDoc.codigo})</TableCell>
                <TableCell sx={{ minWidth: 90 }}>{t('venta.iva')} %</TableCell>
                <TableCell sx={{ minWidth: 90 }}>{t('venta.descuentoPct')}</TableCell>
                <TableCell align="right">{t('compra.total')}</TableCell>
                <TableCell><span className="visualmente-oculto">{t('comun.acciones')}</span></TableCell>
              </TableRow></TableHead>
              <TableBody>
                {lineas.map((l, i) => (
                  <TableRow key={l.producto.id}>
                    <TableCell>{l.producto.nombre} {l.producto.marca} {l.producto.presentacion}
                      <Typography variant="caption" color="text.secondary" component="div">{l.producto.codigoBarras} · {t(`producto.rastreo.${l.producto.tipoRastreo}`)}{l.producto.tieneVencimiento ? ` · ${t('producto.tieneVencimiento')}` : ''}</Typography></TableCell>
                    {['cantidad'].map((c) => (
                      <TableCell key={c}><TextField size="small" value={l[c]} onChange={(e) => editar(i, c, e.target.value)}
                        inputProps={{ inputMode: 'decimal', 'aria-label': `${t('venta.cantidad')} ${i + 1}` }} error={Boolean(err(i, c))} helperText={err(i, c)} /></TableCell>))}
                    <TableCell>
                      <TextField select size="small" value={l.unidad} onChange={(e) => editar(i, 'unidad', e.target.value)} fullWidth
                        inputProps={{ 'aria-label': `${t('venta.presentacion')} ${i + 1}` }}>
                        <MenuItem value="">{l.producto.unidadBase}</MenuItem>
                        {(l.producto.unidades || []).map((u) => <MenuItem key={u.unidadPresentacion} value={u.unidadPresentacion}>{`${u.unidadPresentacion} (×${fmtCantidad(u.factor)})`}</MenuItem>)}
                      </TextField>
                    </TableCell>
                    {[['costoUnitario', t('compra.costoUnitario')], ['porcentajeIva', `${t('venta.iva')} %`], ['porcentajeDescuento', t('venta.descuentoPct')]].map(([c, et]) => (
                      <TableCell key={c}><TextField size="small" value={l[c]} onChange={(e) => editar(i, c, e.target.value)}
                        inputProps={{ inputMode: 'decimal', 'aria-label': `${et} ${i + 1}` }} error={Boolean(err(i, c))} helperText={err(i, c)} /></TableCell>))}
                    <TableCell align="right">{moneda(calculos[i].total, 2)}</TableCell>
                    <TableCell><IconButton aria-label={`${t('venta.quitarLinea')} ${i + 1}`} onClick={() => setLineas((ls) => ls.filter((_, j) => j !== i))}><DeleteIcon /></IconButton></TableCell>
                  </TableRow>))}
              </TableBody>
            </Table>
          </TableContainer>)}
        <TextField label={t('comun.observaciones')} value={observaciones} onChange={(e) => setObservaciones(e.target.value.slice(0, 255))}
          fullWidth multiline minRows={2} margin="normal" error={Boolean(errorObs)} helperText={errorObs ? t('errores.sinHtml') : `${observaciones.length}/255`} />
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} gap={2}>
          <Typography>{t('venta.subtotal')}: {moneda(total.subtotal, 2)} · {t('venta.descuentos')}: {moneda(total.descuentos, 2)} · {t('venta.impuestos')}: {moneda(total.impuestos, 2)} · <strong>{t('compra.total')}: {moneda(total.total, 2)}</strong>
            {!monedaDoc.funcional && <Typography variant="caption" color="text.secondary" component="span"> · {t('moneda.equivalenteCop')} ≈ {fmtMoneda(enCop(total.total, tasa), 2)} ({tasaTexto(monedaDoc.codigo, monedaDoc.tasaVigente)}, {fecha(monedaDoc.fechaTasa)})</Typography>}</Typography>
          <Button variant="contained" disabled={!enLinea || enviando} onClick={() => { setIntentado(true); setErrorServidor(null); if (valido) setConfirmando(true); }}>{t('compra.registrar')}</Button>
        </Stack>
      </Paper>
      <ConfirmDialog abierto={confirmando} titulo={t('compra.confirmarTitulo')} color="primary" textoConfirmar={t('compra.registrar')}
        cargando={enviando} onCancelar={() => setConfirmando(false)} onConfirmar={registrar}
        mensaje={t('compra.confirmarMensaje', { proveedor: proveedor?.nombre ?? '', lineas: lineas.length, total: moneda(total.total, 2) })} />
    </section>
  );
}
