/*
 * Nombre: ProductosPage.jsx
 * Descripción: Catálogo de productos (RF-01, RF-02, RF-12): tabla paginada con búsqueda, formulario Formik+Yup con datos comerciales (categoría, IVA, recargo, stock mínimo, garantía), activación/desactivación, presentaciones (conversiones), exportación a Excel y consulta sin conexión desde la copia local del catálogo.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 2.0.0
 */
import { useCallback, useEffect, useState } from 'react';
import { Alert, Box, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel,
  FormHelperText, Grid, IconButton, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TextField, Tooltip, Typography } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
import ToggleOffIcon from '@mui/icons-material/ToggleOff';
import StraightenIcon from '@mui/icons-material/Straighten';
import SyncIcon from '@mui/icons-material/Sync';
import { Form, Formik } from 'formik';
import { useTranslation } from 'react-i18next';
import { api } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import BotonDescarga from '../components/BotonDescarga';
import ConfirmDialog from '../components/ConfirmDialog';
import EstadoChip from '../components/EstadoChip';
import FormTextField from '../components/FormTextField';
import { useNotify } from '../components/Notifier';
import PageHeader from '../components/PageHeader';
import TablaEstado from '../components/TablaEstado';
import useEnLinea from '../hooks/useEnLinea';
import { buscarLocal, leerCatalogo, sincronizarCatalogo } from '../offline/catalogo';
import { cantidad, fechaHora, moneda, porcentaje } from '../utils/formato';
import { mensajeDeError, traducirValidacion } from '../utils/mensajes';
import { puede } from '../utils/permisos';
import { numero } from '../validation/lineas';
import { productoSchema, SIN_HTML } from '../validation/schemas';

const VACIO = { nombre: '', marca: '', presentacion: '', codigoBarras: '', unidadBase: 'unidad', tipoRastreo: 'NINGUNO', tieneVencimiento: false,
  tieneGarantia: false, descripcion: '', categoria: '', porcentajeIva: '', porcentajeIncremento: '', stockMinimo: '', mesesGarantia: '' };

/** Valores del formulario desde la respuesta de la API (el recargo se guarda como fracción y se edita como porcentaje). */
export const aFormulario = (p) => ({
  ...VACIO, ...Object.fromEntries(Object.entries(p).filter(([, v]) => v !== null && v !== undefined)),
  porcentajeIva: p.porcentajeIva != null ? String(Number(p.porcentajeIva)) : '',
  porcentajeIncremento: p.porcentajeIncremento != null ? String(Math.round(Number(p.porcentajeIncremento) * 10000) / 100) : '',
  stockMinimo: p.stockMinimo != null ? String(Number(p.stockMinimo)) : '',
  mesesGarantia: p.mesesGarantia != null ? String(p.mesesGarantia) : '',
});

/** Cuerpo para la API desde el formulario. */
export const aPeticion = (v) => {
  const texto = (s) => (s && s.trim() ? s.trim() : null);
  const dec = (s) => (String(s ?? '').trim() ? numero(s) : null);
  return {
    nombre: v.nombre.trim(), marca: v.marca.trim(), presentacion: v.presentacion.trim(), codigoBarras: v.codigoBarras.trim(),
    unidadBase: v.unidadBase.trim(), tipoRastreo: v.tipoRastreo, tieneVencimiento: v.tieneVencimiento, tieneGarantia: v.tieneGarantia,
    descripcion: texto(v.descripcion), categoria: texto(v.categoria), porcentajeIva: dec(v.porcentajeIva),
    porcentajeIncremento: String(v.porcentajeIncremento ?? '').trim() ? Math.round(numero(v.porcentajeIncremento) * 100) / 10000 : null,
    stockMinimo: dec(v.stockMinimo), mesesGarantia: v.tieneGarantia && String(v.mesesGarantia).trim() ? Number(v.mesesGarantia) : null,
  };
};

/** Diálogo de presentaciones (conversiones a la unidad base). */
function UnidadesDialog({ producto, onCerrar }) {
  const { t } = useTranslation();
  const notificar = useNotify();
  const [filas, setFilas] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  useEffect(() => {
    if (!producto) return;
    setCargando(true); setFalla(null);
    api.productos.unidades(producto.id)
      .then((u) => setFilas(u.map((x) => ({ unidadPresentacion: x.unidadPresentacion, factor: String(Number(x.factor)), precisionDecimales: String(x.precisionDecimales) }))))
      .catch((e) => setFalla(mensajeDeError(t, e))).finally(() => setCargando(false));
  }, [producto, t]);
  const errores = filas.map((f) => ({
    unidadPresentacion: !f.unidadPresentacion.trim() ? t('errores.obligatorio') : !SIN_HTML.test(f.unidadPresentacion) ? t('errores.sinHtml') : null,
    factor: !/^\d+([.,]\d{1,4})?$/.test(String(f.factor).trim()) || numero(f.factor) <= 0 ? t('errores.positivo') : null,
    precisionDecimales: !/^[0-4]$/.test(String(f.precisionDecimales).trim()) ? t('producto.precisionInvalida') : null,
  }));
  const editar = (i, c, v) => setFilas((fs) => fs.map((f, j) => (j === i ? { ...f, [c]: v } : f)));
  const guardar = async () => {
    if (!filas.length || errores.some((e) => Object.values(e).some(Boolean))) { setFalla(t('producto.revisePresentaciones')); return; }
    setEnviando(true); setFalla(null);
    try {
      await api.productos.configurarUnidades(producto.id, filas.map((f) => ({ unidadPresentacion: f.unidadPresentacion.trim(), factor: numero(f.factor), precisionDecimales: Number(f.precisionDecimales) })));
      notificar(t('comun.guardado')); onCerrar();
    } catch (e) { setFalla(mensajeDeError(t, e)); }
    finally { setEnviando(false); }
  };
  return (
    <Dialog open={Boolean(producto)} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="sm" aria-labelledby="unidades-titulo">
      <DialogTitle id="unidades-titulo">{t('producto.presentaciones', { producto: producto?.nombre })}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" gutterBottom>{t('producto.presentacionesAyuda', { unidad: producto?.unidadBase })}</Typography>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        {cargando ? <CircularProgress size={24} aria-label={t('comun.cargando')} /> : filas.map((f, i) => (
          <Stack key={i} direction={{ xs: 'column', sm: 'row' }} gap={1} alignItems={{ sm: 'flex-start' }} sx={{ mb: 1 }}>
            <TextField size="small" label={t('producto.unidadPresentacion')} value={f.unidadPresentacion} onChange={(e) => editar(i, 'unidadPresentacion', e.target.value.slice(0, 20))}
              error={Boolean(errores[i].unidadPresentacion)} helperText={errores[i].unidadPresentacion} />
            <TextField size="small" label={t('producto.factor')} value={f.factor} onChange={(e) => editar(i, 'factor', e.target.value)} inputProps={{ inputMode: 'decimal' }}
              error={Boolean(errores[i].factor)} helperText={errores[i].factor} />
            <TextField size="small" label={t('producto.precision')} value={f.precisionDecimales} onChange={(e) => editar(i, 'precisionDecimales', e.target.value)} inputProps={{ inputMode: 'numeric' }}
              error={Boolean(errores[i].precisionDecimales)} helperText={errores[i].precisionDecimales} sx={{ width: 120 }} />
            <IconButton aria-label={`${t('comun.eliminar')} ${f.unidadPresentacion}`} onClick={() => setFilas((fs) => fs.filter((_, j) => j !== i))}><DeleteIcon /></IconButton>
          </Stack>))}
        <Button startIcon={<AddIcon />} onClick={() => setFilas((fs) => [...fs, { unidadPresentacion: '', factor: '', precisionDecimales: '0' }])}>{t('producto.agregarPresentacion')}</Button>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={guardar} disabled={enviando || cargando}>{t('comun.guardar')}</Button>
      </DialogActions>
    </Dialog>
  );
}

/**
 * Página de gestión de productos.
 * @returns {JSX.Element} Página.
 */
export default function ProductosPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const enLinea = useEnLinea();
  const puedeEscribir = puede(rol, 'productos.escribir');
  const puedeEliminar = puede(rol, 'productos.eliminar');
  const verCostos = puede(rol, 'inventario.costos');

  const [datos, setDatos] = useState({ contenido: [], totalElementos: 0 });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(10);
  const [q, setQ] = useState('');
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState(null);
  const [local, setLocal] = useState(null);   // { productos, sincronizado } cuando se consulta sin conexión
  const [editando, setEditando] = useState(null); // null = cerrado, {} = nuevo, objeto = edición
  const [aEliminar, setAEliminar] = useState(null);
  const [aCambiar, setACambiar] = useState(null);
  const [unidades, setUnidades] = useState(null);
  const [sincronizando, setSincronizando] = useState(false);

  const cargarLocal = useCallback(async () => { setLocal(await leerCatalogo()); }, []);

  const cargar = useCallback(async () => {
    if (!enLinea) { await cargarLocal(); return; }
    setCargando(true); setError(null);
    try { setDatos(await api.productos.listar({ q, pagina, tamano })); setLocal(null); }
    catch (e) {
      if (!e?.response) await cargarLocal();   // sin respuesta del servidor: se muestra la copia local
      else { setError(e); notificar(mensajeDeError(t, e), 'error'); }
    } finally { setCargando(false); }
  }, [q, pagina, tamano, notificar, t, enLinea, cargarLocal]);

  useEffect(() => { cargar(); }, [cargar]);

  const guardar = async (valores, { setSubmitting }) => {
    try {
      const cuerpo = aPeticion(valores);
      if (editando?.id) await api.productos.actualizar(editando.id, cuerpo); else await api.productos.crear(cuerpo);
      notificar(t('comun.guardado'));
      setEditando(null);
      cargar();
    } catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setSubmitting(false); }
  };

  const eliminar = async () => {
    try { await api.productos.eliminar(aEliminar.id); notificar(t('comun.eliminado')); setAEliminar(null); cargar(); }
    catch (e) { setAEliminar(null); notificar(mensajeDeError(t, e), 'error'); }
  };
  const cambiarEstado = async () => {
    try { await api.productos.cambiarEstado(aCambiar.id, !aCambiar.activo); notificar(t(aCambiar.activo ? 'producto.desactivado' : 'producto.activado')); setACambiar(null); cargar(); }
    catch (e) { setACambiar(null); notificar(mensajeDeError(t, e), 'error'); }
  };
  const sincronizar = async () => {
    setSincronizando(true);
    try { const r = await sincronizarCatalogo((p) => api.catalogo(p)); notificar(t('producto.sincronizado', { n: r.total })); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setSincronizando(false); }
  };

  const filasLocales = local ? buscarLocal(local.productos, q) : [];
  const enModoLocal = Boolean(local);
  const filas = enModoLocal ? filasLocales.slice(pagina * tamano, pagina * tamano + tamano) : datos.contenido;
  const total = enModoLocal ? filasLocales.length : datos.totalElementos;

  return (
    <section aria-labelledby="productos-titulo">
      <PageHeader id="productos-titulo" titulo={t('producto.titulo')} descripcion={t('producto.descripcion')}
        acciones={<>
          {!enModoLocal && <BotonDescarga obtener={() => api.exportar('productos')} />}
          {enLinea && <Button variant="outlined" startIcon={sincronizando ? <CircularProgress size={16} /> : <SyncIcon />} onClick={sincronizar} disabled={sincronizando}>{t('producto.sincronizar')}</Button>}
          {puedeEscribir && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditando({})} disabled={!enLinea}>{t('producto.nuevo')}</Button>}
        </>} />
      {enModoLocal && (
        <Alert severity="info" sx={{ mb: 2 }} role="status">
          {local.sincronizado ? t('producto.modoLocal', { fecha: fechaHora(local.sincronizado), n: local.productos.length }) : t('producto.sinCopiaLocal')}
        </Alert>)}
      <TextField type="search" label={t('comun.buscar')} placeholder={t('producto.buscarPlaceholder')} value={q} fullWidth sx={{ mb: 2 }}
        onChange={(e) => { setQ(e.target.value.slice(0, 100)); setPagina(0); }} />
      <Paper>
        <TableContainer>
          <Table aria-label={t('producto.titulo')} size="small">
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell><TableCell>{t('producto.nombre')}</TableCell><TableCell>{t('producto.marca')}</TableCell>
                <TableCell>{t('producto.presentacion')}</TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('producto.codigoBarras')}</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{enModoLocal ? t('inventario.estados.DISPONIBLE') : t('producto.categoria')}</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{enModoLocal ? t('inventario.precioVigente') : t('producto.iva')}</TableCell>
                {!enModoLocal && verCostos && <TableCell align="right" sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{t('producto.costoPromedio')}</TableCell>}
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('producto.tipoRastreo')}</TableCell>
                <TableCell>{t('comun.estado')}</TableCell>
                {puedeEscribir && !enModoLocal && <TableCell align="right">{t('comun.acciones')}</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              <TablaEstado columnas={11} cargando={cargando} error={!enModoLocal ? error : null} vacio={!filas.length} onReintentar={cargar} />
              {!cargando && filas.map((p) => (
                <TableRow key={p.id} hover>
                  <TableCell>{p.id}</TableCell><TableCell>{p.nombre}</TableCell><TableCell>{p.marca}</TableCell><TableCell>{p.presentacion}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{p.codigoBarras}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{enModoLocal ? `${cantidad(p.disponible)} ${p.unidadBase}` : p.categoria || '—'}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{enModoLocal ? moneda(p.precioVigente) : porcentaje(p.porcentajeIva)}</TableCell>
                  {!enModoLocal && verCostos && <TableCell align="right" sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{moneda(p.costoPromedio, 2)}</TableCell>}
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {t(`producto.rastreo.${p.tipoRastreo}`)}{p.tieneVencimiento && <Chip size="small" variant="outlined" sx={{ ml: 0.5 }} label={t('producto.vence')} />}
                  </TableCell>
                  <TableCell><EstadoChip estado={p.activo === false ? 'INACTIVO' : 'ACTIVO'} /></TableCell>
                  {puedeEscribir && !enModoLocal && (
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <IconButton aria-label={`${t('comun.editar')} ${p.nombre}`} onClick={() => setEditando(p)}><EditIcon /></IconButton>
                      <Tooltip title={t('producto.presentacionesCorto')}><IconButton aria-label={`${t('producto.presentacionesCorto')} ${p.nombre}`} onClick={() => setUnidades(p)}><StraightenIcon /></IconButton></Tooltip>
                      <Tooltip title={p.activo ? t('producto.desactivar') : t('producto.activar')}>
                        <IconButton aria-label={`${p.activo ? t('producto.desactivar') : t('producto.activar')} ${p.nombre}`} onClick={() => setACambiar(p)}>{p.activo ? <ToggleOnIcon color="success" /> : <ToggleOffIcon />}</IconButton>
                      </Tooltip>
                      {puedeEliminar && <IconButton aria-label={`${t('comun.eliminar')} ${p.nombre}`} onClick={() => setAEliminar(p)}><DeleteIcon /></IconButton>}
                    </TableCell>)}
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={total} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, x) => setPagina(x)}
          onRowsPerPageChange={(e) => { setTamano(Number(e.target.value)); setPagina(0); }} />
      </Paper>

      <Dialog open={editando !== null} onClose={() => setEditando(null)} fullWidth maxWidth="md" aria-labelledby="producto-form-titulo">
        <DialogTitle id="producto-form-titulo">{editando?.id ? t('producto.editar') : t('producto.nuevo')}</DialogTitle>
        {editando !== null && (
          <Formik initialValues={editando.id ? aFormulario(editando) : VACIO} validationSchema={productoSchema} onSubmit={guardar}>
            {({ values, errors, touched, handleChange, isSubmitting }) => (
              <Form noValidate>
                <DialogContent>
                  <Grid container columnSpacing={2}>
                    <Grid item xs={12} sm={6}><FormTextField name="nombre" label={t('producto.nombre')} required autoFocus /></Grid>
                    <Grid item xs={12} sm={6}><FormTextField name="marca" label={t('producto.marca')} required /></Grid>
                    <Grid item xs={12} sm={6}><FormTextField name="presentacion" label={t('producto.presentacion')} required /></Grid>
                    <Grid item xs={12} sm={6}><FormTextField name="codigoBarras" label={t('producto.codigoBarras')} required inputProps={{ inputMode: 'numeric' }} /></Grid>
                    <Grid item xs={12} sm={6}><FormTextField name="unidadBase" label={t('producto.unidadBase')} required /></Grid>
                    <Grid item xs={12} sm={6}><FormTextField name="categoria" label={t('producto.categoria')} /></Grid>
                    <Grid item xs={12}><FormTextField name="descripcion" label={t('producto.descripcionCampo')} multiline minRows={2} /></Grid>
                    <Grid item xs={12} sm={4}><FormTextField name="porcentajeIva" label={t('producto.iva')} inputProps={{ inputMode: 'decimal' }} helperText={t('producto.ivaAyuda')} /></Grid>
                    <Grid item xs={12} sm={4}><FormTextField name="porcentajeIncremento" label={t('producto.recargo')} inputProps={{ inputMode: 'decimal' }} helperText={t('producto.recargoAyuda')} /></Grid>
                    <Grid item xs={12} sm={4}><FormTextField name="stockMinimo" label={t('producto.stockMinimo')} inputProps={{ inputMode: 'decimal' }} /></Grid>
                    <Grid item xs={12} sm={6}>
                      <FormTextField name="tipoRastreo" label={t('producto.tipoRastreo')}
                        options={['NINGUNO', 'LOTE', 'SERIE'].map((v) => ({ value: v, label: t(`producto.rastreo.${v}`) }))} />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      {values.tieneGarantia && <FormTextField name="mesesGarantia" label={t('producto.mesesGarantia')} required inputProps={{ inputMode: 'numeric' }} />}
                    </Grid>
                  </Grid>
                  <Box>
                    <FormControlLabel control={<Checkbox name="tieneVencimiento" checked={values.tieneVencimiento} onChange={handleChange} />} label={t('producto.tieneVencimiento')} />
                    <FormControlLabel control={<Checkbox name="tieneGarantia" checked={values.tieneGarantia} onChange={handleChange} />} label={t('producto.tieneGarantia')} />
                    {touched.tieneVencimiento && errors.tieneVencimiento && <FormHelperText error>{traducirValidacion(t, errors.tieneVencimiento)}</FormHelperText>}
                    {values.tieneVencimiento && values.tipoRastreo !== 'LOTE' && <FormHelperText error>{t('errores.vencimientoLote')}</FormHelperText>}
                  </Box>
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => setEditando(null)}>{t('comun.cancelar')}</Button>
                  <Button type="submit" variant="contained" disabled={isSubmitting}>{t('comun.guardar')}</Button>
                </DialogActions>
              </Form>)}
          </Formik>)}
      </Dialog>
      <ConfirmDialog abierto={Boolean(aEliminar)} titulo={`${t('comun.eliminar')}: ${aEliminar?.nombre ?? ''}`} mensaje={t('comun.confirmarEliminar')}
        onConfirmar={eliminar} onCancelar={() => setAEliminar(null)} />
      <ConfirmDialog abierto={Boolean(aCambiar)} titulo={aCambiar?.activo ? t('producto.desactivar') : t('producto.activar')} color={aCambiar?.activo ? 'warning' : 'primary'}
        mensaje={aCambiar?.activo ? t('producto.desactivarMensaje', { producto: aCambiar?.nombre }) : t('producto.activarMensaje', { producto: aCambiar?.nombre })}
        textoConfirmar={aCambiar?.activo ? t('producto.desactivar') : t('producto.activar')} onConfirmar={cambiarEstado} onCancelar={() => setACambiar(null)} />
      <UnidadesDialog producto={unidades} onCerrar={() => setUnidades(null)} />
    </section>
  );
}
