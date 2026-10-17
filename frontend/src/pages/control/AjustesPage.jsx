/*
 * Nombre: AjustesPage.jsx
 * Descripción: Ajustes de inventario con aprobación (RF-14, RN-08): solicitud con producto, lote, tipo, cantidad y motivo; listado por estado; aprobación o rechazo (con motivo) por Gerencia/Administrador sin autoaprobación.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Stack, Table,
  TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import { Filtros } from '../../components/Filtros';
import MotivoDialog from '../../components/MotivoDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import ProductoSelector from '../../components/ProductoSelector';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { nuevaClaveOperacion } from '../../utils/archivos';
import { cantidad, fechaHora } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { errorCantidad, numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

function SolicitudDialog({ abierto, onCerrar, onHecha }) {
  const { t } = useTranslation();
  const [producto, setProducto] = useState(null);
  const [v, setV] = useState({ tipo: 'SALIDA', cantidad: '', motivo: '', observaciones: '', idLote: '' });
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  const clave = useRef(nuevaClaveOperacion());
  const lotes = useCarga(() => (producto?.tipoRastreo === 'LOTE' ? api.inventario.lotes(producto.id) : Promise.resolve([])), [producto?.id]);
  useEffect(() => { if (abierto) { setProducto(null); setV({ tipo: 'SALIDA', cantidad: '', motivo: '', observaciones: '', idLote: '' }); setIntentado(false); setFalla(null); clave.current = nuevaClaveOperacion(); } }, [abierto]);
  const esLote = producto?.tipoRastreo === 'LOTE';
  const errores = {
    producto: !producto ? t('errores.obligatorio') : null,
    cantidad: errorCantidad(v.cantidad) && t(errorCantidad(v.cantidad)),
    motivo: !v.motivo.trim() ? t('errores.obligatorio') : !SIN_HTML.test(v.motivo) ? t('errores.sinHtml') : null,
    observaciones: v.observaciones && !SIN_HTML.test(v.observaciones) ? t('errores.sinHtml') : null,
    idLote: esLote && v.tipo === 'ENTRADA' && !v.idLote ? t('ajuste.loteEntrada') : null,
  };
  const e = (k) => (intentado ? errores[k] : null);
  const enviar = async () => {
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setEnviando(true); setFalla(null);
    try {
      const a = await api.ajustes.solicitar({ productoId: producto.id, tipo: v.tipo, cantidad: numero(v.cantidad), motivo: v.motivo.trim(),
        observaciones: v.observaciones.trim() || null, idOperacion: clave.current, ...(v.idLote ? { idLote: Number(v.idLote) } : {}) });
      onHecha(a);
    } catch (x) { setFalla(mensajeDeError(t, x)); }
    finally { setEnviando(false); }
  };
  return (
    <Dialog open={abierto} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="sm" aria-labelledby="ajuste-titulo">
      <DialogTitle id="ajuste-titulo">{t('ajuste.nuevo')}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <Typography variant="body2" color="text.secondary">{t('ajuste.ayuda')}</Typography>
        <ProductoSelector id="ajuste-producto" valor={producto} onCambio={setProducto} requerido error={e('producto')} autoFocus mostrarPrecio={false} />
        <TextField select label={t('ajuste.tipo')} value={v.tipo} onChange={(x) => setV({ ...v, tipo: x.target.value })} margin="dense" fullWidth>
          <MenuItem value="ENTRADA">{t('ajuste.tipos.ENTRADA')}</MenuItem>
          <MenuItem value="SALIDA">{t('ajuste.tipos.SALIDA')}</MenuItem>
        </TextField>
        {esLote && (
          <TextField select label={t('inventario.lote')} value={v.idLote} onChange={(x) => setV({ ...v, idLote: x.target.value })} margin="dense" fullWidth
            error={Boolean(e('idLote'))} helperText={e('idLote')}>
            <MenuItem value="">{t('inventario.sinLoteEspecifico')}</MenuItem>
            {(lotes.datos || []).map((l) => <MenuItem key={l.id} value={l.id}>{`${l.codigo} · ${cantidad(l.disponible)}`}</MenuItem>)}
          </TextField>)}
        <TextField label={t('movimiento.cantidad')} value={v.cantidad} onChange={(x) => setV({ ...v, cantidad: x.target.value })} required margin="dense" fullWidth
          inputProps={{ inputMode: 'decimal' }} error={Boolean(e('cantidad'))} helperText={e('cantidad')} />
        <TextField label={t('comun.motivo')} value={v.motivo} onChange={(x) => setV({ ...v, motivo: x.target.value.slice(0, 255) })} required margin="dense" fullWidth
          error={Boolean(e('motivo'))} helperText={e('motivo')} />
        <TextField label={t('comun.observaciones')} value={v.observaciones} onChange={(x) => setV({ ...v, observaciones: x.target.value.slice(0, 500) })} margin="dense" fullWidth multiline minRows={2}
          error={Boolean(e('observaciones'))} helperText={e('observaciones')} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando} startIcon={enviando ? <CircularProgress size={16} color="inherit" /> : null}>{t('ajuste.solicitar')}</Button>
      </DialogActions>
    </Dialog>
  );
}

export default function AjustesPage() {
  const { t } = useTranslation();
  const { rol, usuario } = useAuth();
  const notificar = useNotify();
  const [estado, setEstado] = useState('PENDIENTE');
  const [pagina, setPagina] = useState(0);
  const [nuevo, setNuevo] = useState(false);
  const [aprobar, setAprobar] = useState(null);
  const [rechazar, setRechazar] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const { datos, cargando, error, recargar } = useCarga(() => api.ajustes.listar({ estado: estado || undefined, pagina, tamano: 20 }), [estado, pagina]);
  const filas = datos?.contenido || [];
  const decidir = puede(rol, 'ajustes.decidir');

  const confirmarAprobacion = async () => {
    setEnviando(true);
    try { await api.ajustes.aprobar(aprobar.id); notificar(t('ajuste.aprobado')); setAprobar(null); recargar(); }
    catch (x) { notificar(mensajeDeError(t, x), 'error'); }
    finally { setEnviando(false); }
  };
  const confirmarRechazo = async (motivo) => {
    try { await api.ajustes.rechazar(rechazar.id, motivo); notificar(t('ajuste.rechazado')); setRechazar(null); recargar(); }
    catch (x) { notificar(mensajeDeError(t, x), 'error'); }
  };

  return (
    <section aria-labelledby="ajustes-titulo">
      <PageHeader id="ajustes-titulo" titulo={t('ajuste.titulo')} descripcion={t('ajuste.descripcion')}
        acciones={puede(rol, 'ajustes.solicitar') && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setNuevo(true)}>{t('ajuste.nuevo')}</Button>} />
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField select size="small" label={t('comun.estado')} value={estado} onChange={(x) => { setEstado(x.target.value); setPagina(0); }} sx={{ minWidth: 180 }}>
          <MenuItem value="">{t('comun.todos')}</MenuItem>
          {['PENDIENTE', 'APROBADO', 'RECHAZADO'].map((s) => <MenuItem key={s} value={s}>{t(`estados.${s}`)}</MenuItem>)}
        </TextField>
      </Filtros>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('ajuste.titulo')}>
            <TableHead><TableRow>
              <TableCell>{t('ajuste.numero')}</TableCell><TableCell>{t('venta.producto')}</TableCell><TableCell>{t('ajuste.tipo')}</TableCell>
              <TableCell align="right">{t('movimiento.cantidad')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('comun.motivo')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('ajuste.solicitante')}</TableCell>
              <TableCell>{t('comun.estado')}</TableCell><TableCell align="right">{t('comun.acciones')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={8} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((a) => {
                const propio = Boolean(a.solicitante && usuario?.correo && a.solicitante.toLowerCase() === usuario.correo.toLowerCase());
                return (
                  <TableRow key={a.id}>
                    <TableCell>{a.numero}<Typography variant="caption" component="div" color="text.secondary">{fechaHora(a.fechaSolicitud)}</Typography></TableCell>
                    <TableCell>{a.producto}{a.lote ? <Typography variant="caption" component="div">{t('inventario.lote')} {a.lote}</Typography> : null}{a.conteo ? <Typography variant="caption" component="div">{t('conteo.sesion')} {a.conteo}</Typography> : null}</TableCell>
                    <TableCell>{t(`ajuste.tipos.${a.tipo}`)}</TableCell>
                    <TableCell align="right">{cantidad(a.cantidad)}<Typography variant="caption" component="div" color="text.secondary">{t('ajuste.saldoActual')}: {cantidad(a.saldoActual)}</Typography></TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{a.motivo}{a.motivoRechazo && <Typography variant="caption" component="div" color="error">{a.motivoRechazo}</Typography>}</TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{a.solicitante}{a.aprobador && <Typography variant="caption" component="div" color="text.secondary">{t('ajuste.decidio')}: {a.aprobador}</Typography>}</TableCell>
                    <TableCell><EstadoChip estado={a.estado} /></TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {a.estado === 'PENDIENTE' && decidir && (propio
                        ? <Typography variant="caption" color="text.secondary">{t('ajuste.noAutoaprobar')}</Typography>
                        : <Stack direction="row" gap={1} justifyContent="flex-end">
                          <Button size="small" color="success" onClick={() => setAprobar(a)}>{t('comun.aprobar')}</Button>
                          <Button size="small" color="error" onClick={() => setRechazar(a)}>{t('comun.rechazar')}</Button>
                        </Stack>)}
                    </TableCell>
                  </TableRow>);
              })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
          onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
      </Paper>
      <SolicitudDialog abierto={nuevo} onCerrar={() => setNuevo(false)} onHecha={(a) => { setNuevo(false); notificar(t('ajuste.solicitado', { numero: a.numero })); recargar(); }} />
      <ConfirmDialog abierto={Boolean(aprobar)} titulo={t('ajuste.aprobarTitulo', { numero: aprobar?.numero })} color="success" textoConfirmar={t('comun.aprobar')} cargando={enviando}
        mensaje={t('ajuste.aprobarMensaje', { tipo: aprobar ? t(`ajuste.tipos.${aprobar.tipo}`) : '', cantidad: cantidad(aprobar?.cantidad), producto: aprobar?.producto })}
        onConfirmar={confirmarAprobacion} onCancelar={() => setAprobar(null)} />
      <MotivoDialog abierto={Boolean(rechazar)} titulo={t('ajuste.rechazarTitulo', { numero: rechazar?.numero })} textoConfirmar={t('comun.rechazar')}
        onConfirmar={confirmarRechazo} onCancelar={() => setRechazar(null)} />
    </section>
  );
}
