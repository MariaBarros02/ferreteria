/*
 * Nombre: PreciosPage.jsx
 * Descripción: Gestión de precios (RF-07, RF-16, RN-07): propuestas por recargo, margen o valor manual (con justificación), aprobación o rechazo por Gerencia sin autoaprobación, y consulta del historial de precios por producto.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useState } from 'react';
import { Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Stack, Tab, Table,
  TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import MotivoDialog from '../../components/MotivoDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import ProductoSelector from '../../components/ProductoSelector';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { fechaHora, moneda, porcentaje } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

const PCT = /^\d{1,2}([.,]\d{1,2})?$/;
const IMPORTE = /^\d+([.,]\d{1,4})?$/;

/** Porcentaje expresado como fracción (30 → 0.3), como lo espera la API. */
export const aFraccion = (pct) => Math.round(numero(pct) * 100) / 10000;

function PropuestaDialog({ abierto, onCerrar, onHecha }) {
  const { t } = useTranslation();
  const [producto, setProducto] = useState(null);
  const [v, setV] = useState({ modo: 'recargo', porcentaje: '', precioManual: '', motivo: '' });
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  useEffect(() => { if (abierto) { setProducto(null); setV({ modo: 'recargo', porcentaje: '', precioManual: '', motivo: '' }); setIntentado(false); setFalla(null); } }, [abierto]);
  const manual = v.modo === 'manual';
  const errores = {
    producto: !producto ? t('errores.obligatorio') : null,
    porcentaje: !manual && v.porcentaje && !PCT.test(v.porcentaje) ? t('errores.porcentaje') : null,
    precioManual: manual && (!v.precioManual || !IMPORTE.test(v.precioManual) || numero(v.precioManual) <= 0) ? t('errores.positivo') : null,
    motivo: manual && !v.motivo.trim() ? t('errores.obligatorio') : v.motivo && !SIN_HTML.test(v.motivo) ? t('errores.sinHtml') : null,
  };
  if (!manual && v.modo === 'margen' && v.porcentaje && numero(v.porcentaje) >= 100) errores.porcentaje = t('precio.margenMaximo');
  const e = (k) => (intentado ? errores[k] : null);
  const enviar = async () => {
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setEnviando(true); setFalla(null);
    try {
      const r = await api.precios.proponer({ productoId: producto.id, modo: v.modo,
        ...(!manual && v.porcentaje ? { porcentaje: aFraccion(v.porcentaje) } : {}),
        ...(manual ? { precioManual: numero(v.precioManual) } : {}), motivo: v.motivo.trim() || null });
      onHecha(r);
    } catch (x) { setFalla(mensajeDeError(t, x)); }
    finally { setEnviando(false); }
  };
  return (
    <Dialog open={abierto} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="sm" aria-labelledby="propuesta-titulo">
      <DialogTitle id="propuesta-titulo">{t('precio.nueva')}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <ProductoSelector id="precio-producto" valor={producto} onCambio={setProducto} requerido error={e('producto')} autoFocus />
        {producto && <Typography variant="body2" color="text.secondary">{t('precio.vigenteActual')}: {moneda(producto.precioVigente)}</Typography>}
        <TextField select label={t('precio.modo')} value={v.modo} onChange={(x) => setV({ ...v, modo: x.target.value })} margin="dense" fullWidth
          helperText={t(`precio.modoAyuda.${v.modo}`)}>
          {['recargo', 'margen', 'manual'].map((m) => <MenuItem key={m} value={m}>{t(`precio.modos.${m}`)}</MenuItem>)}
        </TextField>
        {manual ? (
          <TextField label={t('precio.precioManual')} value={v.precioManual} onChange={(x) => setV({ ...v, precioManual: x.target.value })} required margin="dense" fullWidth
            inputProps={{ inputMode: 'decimal' }} error={Boolean(e('precioManual'))} helperText={e('precioManual')} />
        ) : (
          <TextField label={t('precio.porcentaje')} value={v.porcentaje} onChange={(x) => setV({ ...v, porcentaje: x.target.value })} margin="dense" fullWidth
            inputProps={{ inputMode: 'decimal' }} error={Boolean(e('porcentaje'))} helperText={e('porcentaje') || t('precio.porcentajeAyuda')} />
        )}
        <TextField label={t('precio.justificacion')} value={v.motivo} onChange={(x) => setV({ ...v, motivo: x.target.value.slice(0, 255) })} required={manual} margin="dense" fullWidth multiline
          error={Boolean(e('motivo'))} helperText={e('motivo')} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando} startIcon={enviando ? <CircularProgress size={16} color="inherit" /> : null}>{t('precio.proponer')}</Button>
      </DialogActions>
    </Dialog>
  );
}

function TablaPropuestas({ estado }) {
  const { t } = useTranslation();
  const { rol, usuario } = useAuth();
  const notificar = useNotify();
  const [pagina, setPagina] = useState(0);
  const [aprobar, setAprobar] = useState(null);
  const [rechazar, setRechazar] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const { datos, cargando, error, recargar } = useCarga(() => api.precios.propuestas({ estado, pagina, tamano: 20 }), [estado, pagina]);
  const filas = datos?.contenido || [];
  const decidir = estado === 'propuesto' && puede(rol, 'precios.aprobar');
  const confirmarAprobacion = async () => {
    setEnviando(true);
    try { await api.precios.aprobar(aprobar.id); notificar(t('precio.aprobado')); setAprobar(null); recargar(); }
    catch (x) { notificar(mensajeDeError(t, x), 'error'); }
    finally { setEnviando(false); }
  };
  const confirmarRechazo = async (motivo) => {
    try { await api.precios.rechazar(rechazar.id, motivo); notificar(t('precio.rechazado')); setRechazar(null); recargar(); }
    catch (x) { notificar(mensajeDeError(t, x), 'error'); }
  };
  return (
    <Paper>
      <TableContainer>
        <Table size="small" aria-label={t(`precio.pestanas.${estado}`)}>
          <TableHead><TableRow>
            <TableCell>{t('venta.producto')}</TableCell>
            <TableCell align="right">{t('producto.costoPromedio')}</TableCell>
            <TableCell>{t('precio.modo')}</TableCell>
            <TableCell align="right">{t('precio.actual')}</TableCell>
            <TableCell align="right">{t('precio.propuesto')}</TableCell>
            <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('precio.margen')}</TableCell>
            <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('precio.proponente')}</TableCell>
            <TableCell>{estado === 'propuesto' ? t('comun.acciones') : t('precio.decision')}</TableCell>
          </TableRow></TableHead>
          <TableBody>
            <TablaEstado columnas={8} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
            {!error && filas.map((p) => {
              const propia = Boolean(p.proponente && usuario?.correo && p.proponente.toLowerCase() === usuario.correo.toLowerCase());
              return (
                <TableRow key={p.id}>
                  <TableCell>{p.producto}<Typography variant="caption" component="div" color="text.secondary">{fechaHora(p.fechaPropuesta)}{p.motivo ? ` · ${p.motivo}` : ''}</Typography></TableCell>
                  <TableCell align="right">{moneda(p.costoPromedio, 2)}</TableCell>
                  <TableCell>{t(`precio.modos.${p.modoCalculo}`, { defaultValue: p.modoCalculo })} {p.porcentajeIncremento != null && p.modoCalculo !== 'manual' ? porcentaje(Number(p.porcentajeIncremento) * 100) : ''}</TableCell>
                  <TableCell align="right">{moneda(p.precioActual)}</TableCell>
                  <TableCell align="right"><strong>{moneda(p.precioPropuesto)}</strong></TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{p.margenPropuesto != null ? porcentaje(Number(p.margenPropuesto) * 100) : '—'}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{p.proponente || t('precio.automatica')}</TableCell>
                  <TableCell>
                    {decidir && (propia ? <Typography variant="caption" color="text.secondary">{t('precio.noAutoaprobar')}</Typography> : (
                      <Stack direction="row" gap={1}>
                        <Button size="small" color="success" onClick={() => setAprobar(p)}>{t('comun.aprobar')}</Button>
                        <Button size="small" color="error" onClick={() => setRechazar(p)}>{t('comun.rechazar')}</Button>
                      </Stack>))}
                    {estado !== 'propuesto' && (<><EstadoChip estado={p.estado} /><Typography variant="caption" component="div">{p.aprobador} {fechaHora(p.fechaDecision)}</Typography>
                      {p.motivoRechazo && <Typography variant="caption" component="div" color="error">{p.motivoRechazo}</Typography>}</>)}
                  </TableCell>
                </TableRow>);
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
        onPageChange={(_, x) => setPagina(x)} labelRowsPerPage={t('comun.filasPorPagina')} />
      <ConfirmDialog abierto={Boolean(aprobar)} titulo={t('precio.aprobarTitulo')} color="success" textoConfirmar={t('comun.aprobar')} cargando={enviando}
        mensaje={t('precio.aprobarMensaje', { producto: aprobar?.producto, actual: moneda(aprobar?.precioActual), propuesto: moneda(aprobar?.precioPropuesto) })}
        onConfirmar={confirmarAprobacion} onCancelar={() => setAprobar(null)} />
      <MotivoDialog abierto={Boolean(rechazar)} titulo={t('precio.rechazarTitulo')} textoConfirmar={t('comun.rechazar')}
        onConfirmar={confirmarRechazo} onCancelar={() => setRechazar(null)} />
    </Paper>
  );
}

function Historial() {
  const { t } = useTranslation();
  const [producto, setProducto] = useState(null);
  const [pagina, setPagina] = useState(0);
  const { datos, cargando, error, recargar } = useCarga(() => (producto ? api.precios.historial(producto.id, { pagina, tamano: 20 }) : Promise.resolve(null)), [producto?.id, pagina]);
  const filas = datos?.contenido || [];
  return (
    <>
      <ProductoSelector id="historial-producto" valor={producto} onCambio={(p) => { setProducto(p); setPagina(0); }} />
      {producto && (
        <Paper sx={{ mt: 2 }}>
          <TableContainer>
            <Table size="small" aria-label={t('precio.historial')}>
              <TableHead><TableRow>
                <TableCell>{t('precio.fechaPropuesta')}</TableCell><TableCell align="right">{t('producto.costoPromedio')}</TableCell>
                <TableCell align="right">{t('precio.propuesto')}</TableCell><TableCell>{t('comun.estado')}</TableCell>
                <TableCell>{t('precio.vigenteDesde')}</TableCell><TableCell>{t('precio.decision')}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                <TablaEstado columnas={6} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
                {!error && filas.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell>{fechaHora(p.fechaPropuesta)}</TableCell><TableCell align="right">{moneda(p.costoPromedio, 2)}</TableCell>
                    <TableCell align="right">{moneda(p.precioPropuesto)}</TableCell><TableCell><EstadoChip estado={p.estado} /></TableCell>
                    <TableCell>{p.fechaVigencia ? fechaHora(p.fechaVigencia) : '—'}</TableCell>
                    <TableCell>{p.aprobador || '—'}{p.motivoRechazo ? ` · ${p.motivoRechazo}` : ''}</TableCell>
                  </TableRow>))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
            onPageChange={(_, x) => setPagina(x)} labelRowsPerPage={t('comun.filasPorPagina')} />
        </Paper>)}
    </>
  );
}

export default function PreciosPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const [pestana, setPestana] = useState('propuesto');
  const [nueva, setNueva] = useState(false);
  const [version, setVersion] = useState(0);
  return (
    <section aria-labelledby="precios-titulo">
      <PageHeader id="precios-titulo" titulo={t('precio.titulo')} descripcion={t('precio.descripcion')}
        acciones={puede(rol, 'precios.proponer') && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setNueva(true)}>{t('precio.nueva')}</Button>} />
      <Tabs value={pestana} onChange={(_, v) => setPestana(v)} aria-label={t('precio.titulo')} sx={{ mb: 2 }} variant="scrollable" allowScrollButtonsMobile>
        {['propuesto', 'aprobado', 'rechazado', 'historial'].map((p) => <Tab key={p} value={p} label={t(`precio.pestanas.${p}`)} />)}
      </Tabs>
      {pestana === 'historial' ? <Historial /> : <TablaPropuestas key={`${pestana}-${version}`} estado={pestana} />}
      <PropuestaDialog abierto={nueva} onCerrar={() => setNueva(false)}
        onHecha={(p) => { setNueva(false); notificar(t('precio.propuestaCreada', { precio: moneda(p.precioPropuesto) })); setPestana('propuesto'); setVersion((x) => x + 1); }} />
    </section>
  );
}
