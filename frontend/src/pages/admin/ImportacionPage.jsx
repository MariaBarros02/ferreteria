/*
 * Nombre: ImportacionPage.jsx
 * Descripción: Importación desde Excel (RF-10, RN-15): plantilla descargable, carga y validación del archivo, vista previa por fila con errores por celda, duplicados y posibles duplicados (selección explícita), confirmación atómica, cancelación, informe de errores e historial.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useMemo, useRef, useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Checkbox, CircularProgress, Grid, MenuItem, Paper, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import BotonDescarga from '../../components/BotonDescarga';
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { fechaHora } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';

/** Tamaño máximo aceptado por el servidor. */
export const MAX_BYTES = 5 * 1024 * 1024;

/** Validación local del archivo antes de subirlo (el servidor revalida tipo, tamaño y contenido). */
export function errorArchivo(archivo) {
  if (!archivo) return 'importacion.seleccioneArchivo';
  if (!/\.xlsx$/i.test(archivo.name)) return 'importacion.soloXlsx';
  if (archivo.size > MAX_BYTES) return 'importacion.archivoGrande';
  if (archivo.size === 0) return 'importacion.archivoVacio';
  return null;
}

/** Cuenta filas por estado. */
export const resumenFilas = (filas) => (filas || []).reduce((r, f) => ({ ...r, [f.estado]: (r[f.estado] || 0) + 1 }), {});

function Resultado({ imp, onCambio }) {
  const { t } = useTranslation();
  const notificar = useNotify();
  const [filtro, setFiltro] = useState('');
  const [pagina, setPagina] = useState(0);
  const [aprobados, setAprobados] = useState([]);
  const [dialogo, setDialogo] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const conteo = useMemo(() => resumenFilas(imp.filas), [imp]);
  const filas = (imp.filas || []).filter((f) => !filtro || f.estado === filtro);
  const visibles = filas.slice(pagina * 25, pagina * 25 + 25);
  const columnas = Object.keys(imp.filas?.[0]?.datos || {}).slice(0, 6);
  const pendiente = imp.estado === 'VALIDADA';
  const importables = (conteo.VALIDA || 0) + aprobados.length;

  const confirmar = async () => {
    setEnviando(true);
    try { const r = await api.importaciones.confirmar(imp.id, aprobados); onCambio(r); notificar(t('importacion.importada', { n: resumenFilas(r.filas).IMPORTADA || 0 })); setDialogo(null); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); setDialogo(null); }
    finally { setEnviando(false); }
  };
  const cancelar = async () => {
    setEnviando(true);
    try { onCambio(await api.importaciones.cancelar(imp.id)); notificar(t('importacion.cancelada')); setDialogo(null); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setEnviando(false); }
  };

  return (
    <Paper sx={{ p: 2, mb: 2 }} aria-labelledby="resultado-titulo">
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" gap={2} mb={2}>
        <Box>
          <Typography id="resultado-titulo" variant="h6" component="h2">{imp.nombreArchivo}</Typography>
          <Typography variant="body2" color="text.secondary">{t(`importacion.tipos.${imp.tipo}`)} · {imp.totalFilas} {t('importacion.filas')} · {imp.usuario} · {fechaHora(imp.fechaCarga)} <EstadoChip estado={imp.estado} /></Typography>
        </Box>
        <Stack direction="row" gap={1} flexWrap="wrap">
          {(conteo.ERROR || 0) + (conteo.DUPLICADO || 0) + (conteo.OMITIDA || 0) > 0 && <BotonDescarga obtener={() => api.importaciones.errores(imp.id)}>{t('importacion.informeErrores')}</BotonDescarga>}
          {pendiente && <Button color="inherit" onClick={() => setDialogo('cancelar')}>{t('importacion.cancelar')}</Button>}
          {pendiente && <Button variant="contained" disabled={importables === 0} onClick={() => setDialogo('confirmar')}>{t('importacion.confirmar', { n: importables })}</Button>}
        </Stack>
      </Stack>
      <Grid container spacing={1} sx={{ mb: 2 }}>
        {['VALIDA', 'ERROR', 'DUPLICADO', 'POSIBLE_DUPLICADO', 'IMPORTADA', 'OMITIDA'].filter((k) => conteo[k]).map((k) => (
          <Grid item xs={6} sm={4} md={2} key={k}>
            <Card variant="outlined" sx={{ cursor: 'pointer', borderColor: filtro === k ? 'primary.main' : undefined }}
              onClick={() => { setFiltro(filtro === k ? '' : k); setPagina(0); }} role="button" tabIndex={0} aria-pressed={filtro === k}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setFiltro(filtro === k ? '' : k); setPagina(0); } }}>
              <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
                <Typography variant="caption" component="p">{t(`estados.${k}`)}</Typography>
                <Typography variant="h6" component="p">{conteo[k]}</Typography>
              </CardContent>
            </Card>
          </Grid>))}
      </Grid>
      {pendiente && conteo.POSIBLE_DUPLICADO > 0 && <Alert severity="info" sx={{ mb: 2 }}>{t('importacion.ayudaPosibles')}</Alert>}
      {pendiente && conteo.ERROR > 0 && <Alert severity="warning" sx={{ mb: 2 }}>{t('importacion.ayudaErrores')}</Alert>}
      <TableContainer>
        <Table size="small" aria-label={t('importacion.vistaPrevia')}>
          <TableHead><TableRow>
            <TableCell>{t('importacion.fila')}</TableCell><TableCell>{t('comun.estado')}</TableCell>
            {columnas.map((c) => <TableCell key={c} sx={{ display: { xs: 'none', md: 'table-cell' } }}>{c}</TableCell>)}
            <TableCell>{t('importacion.observaciones')}</TableCell>
          </TableRow></TableHead>
          <TableBody>
            {visibles.map((f) => (
              <TableRow key={f.fila}>
                <TableCell>{f.fila}</TableCell>
                <TableCell>
                  {pendiente && f.estado === 'POSIBLE_DUPLICADO' ? (
                    <Stack direction="row" alignItems="center">
                      <Checkbox size="small" checked={aprobados.includes(f.fila)} inputProps={{ 'aria-label': t('importacion.importarFila', { fila: f.fila }) }}
                        onChange={(e) => setAprobados((a) => (e.target.checked ? [...a, f.fila] : a.filter((x) => x !== f.fila)))} />
                      <EstadoChip estado={f.estado} />
                    </Stack>) : <EstadoChip estado={f.estado} />}
                </TableCell>
                {columnas.map((c) => <TableCell key={c} sx={{ display: { xs: 'none', md: 'table-cell' } }}>{f.datos?.[c]}</TableCell>)}
                <TableCell>
                  {(f.errores || []).map((e, i) => <Typography key={i} variant="caption" color="error" component="div">{e.columna}: {e.mensaje}</Typography>)}
                  {f.coincidencia && <Typography variant="caption" component="div">{t('importacion.coincide')}: {f.coincidencia}</Typography>}
                </TableCell>
              </TableRow>))}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination component="div" count={filas.length} page={pagina} rowsPerPage={25} rowsPerPageOptions={[25]}
        onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
      <ConfirmDialog abierto={dialogo === 'confirmar'} titulo={t('importacion.confirmarTitulo')} color="primary" textoConfirmar={t('importacion.importar')} cargando={enviando}
        mensaje={t('importacion.confirmarMensaje', { n: importables, omitidas: imp.totalFilas - importables })} onConfirmar={confirmar} onCancelar={() => setDialogo(null)} />
      <ConfirmDialog abierto={dialogo === 'cancelar'} titulo={t('importacion.cancelarTitulo')} mensaje={t('importacion.cancelarMensaje')} textoConfirmar={t('importacion.cancelar')}
        cargando={enviando} onConfirmar={cancelar} onCancelar={() => setDialogo(null)} />
    </Paper>
  );
}

export default function ImportacionPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const tipos = ['productos', 'terceros'].filter((x) => puede(rol, `importacion.${x}`));
  const [tipo, setTipo] = useState(tipos[0] || '');
  const [archivo, setArchivo] = useState(null);
  const [falla, setFalla] = useState(null);
  const [validando, setValidando] = useState(false);
  const [imp, setImp] = useState(null);
  const [pagina, setPagina] = useState(0);
  const entrada = useRef(null);
  const historial = useCarga(() => api.importaciones.historial({ pagina, tamano: 10 }), [pagina]);

  const validar = async () => {
    const e = errorArchivo(archivo);
    if (e) { setFalla(t(e)); return; }
    setValidando(true); setFalla(null);
    try { setImp(await api.importaciones.validar(tipo, archivo)); historial.recargar(); setArchivo(null); if (entrada.current) entrada.current.value = ''; }
    catch (x) { setFalla(mensajeDeError(t, x)); }
    finally { setValidando(false); }
  };
  const abrir = async (id) => {
    try { setImp(await api.importaciones.obtener(id)); window.scrollTo?.({ top: 0, behavior: 'smooth' }); }
    catch (x) { setFalla(mensajeDeError(t, x)); }
  };

  return (
    <section aria-labelledby="importacion-titulo">
      <PageHeader id="importacion-titulo" titulo={t('importacion.titulo')} descripcion={t('importacion.descripcion')} />
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} gap={2} alignItems={{ md: 'center' }}>
          <TextField select size="small" label={t('importacion.tipo')} value={tipo} onChange={(e) => setTipo(e.target.value)} sx={{ minWidth: 180 }}>
            {tipos.map((x) => <MenuItem key={x} value={x}>{t(`importacion.tipos.${x}`)}</MenuItem>)}
          </TextField>
          <BotonDescarga obtener={() => api.importaciones.plantilla(tipo)}>{t('importacion.plantilla')}</BotonDescarga>
          <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
            {archivo ? archivo.name : t('importacion.elegirArchivo')}
            <input ref={entrada} type="file" hidden accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={(e) => { setArchivo(e.target.files?.[0] || null); setFalla(null); }} data-testid="archivo-importacion" />
          </Button>
          <Button variant="contained" onClick={validar} disabled={!archivo || validando || !tipo}
            startIcon={validando ? <CircularProgress size={16} color="inherit" /> : null}>{t('importacion.validar')}</Button>
        </Stack>
        <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1 }}>{t('importacion.reglas')}</Typography>
        {falla && <Alert severity="error" role="alert" sx={{ mt: 2 }}>{falla}</Alert>}
      </Paper>
      {imp && <Resultado key={`${imp.id}-${imp.estado}`} imp={imp} onCambio={(r) => { setImp(r); historial.recargar(); }} />}
      <Paper>
        <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{t('importacion.historial')}</Typography>
        <TableContainer>
          <Table size="small" aria-label={t('importacion.historial')}>
            <TableHead><TableRow>
              <TableCell>{t('importacion.fecha')}</TableCell><TableCell>{t('importacion.tipo')}</TableCell><TableCell>{t('importacion.archivo')}</TableCell>
              <TableCell align="right">{t('importacion.filas')}</TableCell><TableCell>{t('comun.estado')}</TableCell><TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('auditoria.usuario')}</TableCell>
              <TableCell align="right">{t('comun.acciones')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={7} cargando={historial.cargando && !historial.datos} error={historial.error} vacio={!historial.datos?.contenido?.length} onReintentar={historial.recargar} />
              {(historial.datos?.contenido || []).map((h) => (
                <TableRow key={h.id}>
                  <TableCell>{fechaHora(h.fechaCarga)}</TableCell><TableCell>{t(`importacion.tipos.${h.tipo}`)}</TableCell><TableCell>{h.nombreArchivo}</TableCell>
                  <TableCell align="right">{h.totalFilas}</TableCell><TableCell><EstadoChip estado={h.estado} /></TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{h.usuario}</TableCell>
                  <TableCell align="right"><Button size="small" onClick={() => abrir(h.id)}>{t('importacion.ver')}</Button></TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={historial.datos?.totalElementos || 0} page={pagina} rowsPerPage={10} rowsPerPageOptions={[10]}
          onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
      </Paper>
    </section>
  );
}
