/*
 * Nombre: InventarioDialogs.jsx
 * Descripción: Diálogos del módulo de inventario: kardex y lotes de un producto (RF-05, RF-08), cambio de estado disponible/reservado/dañado (RF-05) y creación de reservas (RF-05).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useState } from 'react';
import { Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, Tab, Table,
  TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import EstadoChip from '../../components/EstadoChip';
import { CampoFecha } from '../../components/Filtros';
import ProductoSelector from '../../components/ProductoSelector';
import TablaEstado from '../../components/TablaEstado';
import TerceroSelector from '../../components/TerceroSelector';
import useCarga from '../../hooks/useCarga';
import { cantidad, fecha, fechaHora, hoyIso } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { errorCantidad, numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

export const TIPOS_MOVIMIENTO = ['COMPRA', 'VENTA', 'DEVOLUCION_CLIENTE', 'DEVOLUCION_PROVEEDOR', 'AJUSTE', 'CONTEO', 'RESERVA', 'CAMBIO_ESTADO', 'BAJA_VENCIMIENTO'];

/** Kardex (movimientos) y lotes de un producto. */
export function KardexDialog({ producto, onCerrar }) {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const verKardex = puede(rol, 'inventario.kardex');
  const [pestana, setPestana] = useState(verKardex ? 'kardex' : 'lotes');
  const [f, setF] = useState({ tipo: '', desde: '', hasta: '' });
  const [pagina, setPagina] = useState(0);
  const id = producto?.productoId;
  const params = { ...Object.fromEntries(Object.entries(f).filter(([, v]) => v)), pagina, tamano: 20 };
  const movs = useCarga(() => (id && verKardex ? api.inventario.movimientos(id, params) : Promise.resolve(null)), [id, verKardex, JSON.stringify(params)]);
  const lotes = useCarga(() => (id ? api.inventario.lotes(id) : Promise.resolve([])), [id]);
  const filas = movs.datos?.contenido || [];
  return (
    <Dialog open={Boolean(producto)} onClose={onCerrar} fullWidth maxWidth="lg" aria-labelledby="kardex-titulo">
      <DialogTitle id="kardex-titulo">{producto?.producto}</DialogTitle>
      <DialogContent>
        <Tabs value={pestana} onChange={(_, v) => setPestana(v)} aria-label={t('inventario.detalleProducto')} sx={{ mb: 2 }}>
          {verKardex && <Tab value="kardex" label={t('inventario.kardex')} />}
          <Tab value="lotes" label={t('inventario.lotes')} />
        </Tabs>
        {pestana === 'kardex' && (<>
          <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} mb={2}>
            <TextField select size="small" label={t('movimiento.tipo')} value={f.tipo} onChange={(e) => { setF({ ...f, tipo: e.target.value }); setPagina(0); }} sx={{ minWidth: 200 }}>
              <MenuItem value="">{t('comun.todos')}</MenuItem>
              {TIPOS_MOVIMIENTO.map((x) => <MenuItem key={x} value={x}>{t(`movimiento.tipos.${x}`)}</MenuItem>)}
            </TextField>
            <CampoFecha label={t('comun.desde')} value={f.desde} onChange={(v) => { setF({ ...f, desde: v }); setPagina(0); }} />
            <CampoFecha label={t('comun.hasta')} value={f.hasta} onChange={(v) => { setF({ ...f, hasta: v }); setPagina(0); }} />
          </Stack>
          <TableContainer>
            <Table size="small" aria-label={t('inventario.kardex')}>
              <TableHead><TableRow>
                <TableCell>{t('movimiento.fecha')}</TableCell><TableCell>{t('movimiento.tipo')}</TableCell>
                <TableCell>{t('movimiento.estado')}</TableCell><TableCell align="right">{t('movimiento.cantidad')}</TableCell>
                <TableCell align="right">{t('movimiento.saldoAnterior')}</TableCell><TableCell align="right">{t('movimiento.saldoNuevo')}</TableCell>
                <TableCell>{t('movimiento.documento')}</TableCell><TableCell>{t('comun.motivo')}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                <TablaEstado columnas={8} cargando={movs.cargando && !movs.datos} error={movs.error} vacio={!filas.length} onReintentar={movs.recargar} />
                {!movs.error && filas.map((m) => (
                  <TableRow key={m.id}>
                    <TableCell>{fechaHora(m.fechaHora)}</TableCell>
                    <TableCell>{t(`movimiento.tipos.${m.tipo}`)}</TableCell>
                    <TableCell>{t(`inventario.estados.${m.estadoInventario}`)}{m.idLote ? ` · ${t('inventario.lote')} ${m.idLote}` : ''}</TableCell>
                    <TableCell align="right">{cantidad(m.cantidad)}</TableCell>
                    <TableCell align="right">{cantidad(m.valorAnterior)}</TableCell>
                    <TableCell align="right">{cantidad(m.valorNuevo)}</TableCell>
                    <TableCell>{m.documentoRelacionado}</TableCell>
                    <TableCell>{m.motivo}</TableCell>
                  </TableRow>))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination component="div" count={movs.datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
            onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
        </>)}
        {pestana === 'lotes' && (
          <TableContainer>
            <Table size="small" aria-label={t('inventario.lotes')}>
              <TableHead><TableRow>
                <TableCell>{t('inventario.lote')}</TableCell><TableCell>{t('inventario.vencimiento')}</TableCell>
                <TableCell align="right">{t('inventario.estados.DISPONIBLE')}</TableCell><TableCell align="right">{t('inventario.estados.DANADO')}</TableCell>
                <TableCell>{t('comun.estado')}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                <TablaEstado columnas={5} cargando={lotes.cargando} error={lotes.error} vacio={!lotes.datos?.length} onReintentar={lotes.recargar} textoVacio={t('inventario.sinLotes')} />
                {!lotes.cargando && (lotes.datos || []).map((l) => (
                  <TableRow key={l.id}>
                    <TableCell>{l.codigo}</TableCell>
                    <TableCell>{l.fechaVencimiento ? fecha(l.fechaVencimiento) : t('inventario.sinFecha')}</TableCell>
                    <TableCell align="right">{cantidad(l.disponible)}</TableCell>
                    <TableCell align="right">{cantidad(l.danado)}</TableCell>
                    <TableCell><EstadoChip estado={l.estado} /></TableCell>
                  </TableRow>))}
              </TableBody>
            </Table>
          </TableContainer>)}
      </DialogContent>
      <DialogActions><Button onClick={onCerrar}>{t('comun.cerrar')}</Button></DialogActions>
    </Dialog>
  );
}

/** Cambio de estado del inventario de un producto (disponible ↔ reservado ↔ dañado). */
export function CambioEstadoDialog({ producto, onCerrar, onHecho }) {
  const { t } = useTranslation();
  const [v, setV] = useState({ desde: 'DISPONIBLE', hacia: 'DANADO', cantidad: '', motivo: '', idLote: '' });
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  const lotes = useCarga(() => (producto?.tipoRastreo === 'LOTE' ? api.inventario.lotes(producto.productoId) : Promise.resolve([])), [producto?.productoId]);
  useEffect(() => { if (producto) { setV({ desde: 'DISPONIBLE', hacia: 'DANADO', cantidad: '', motivo: '', idLote: '' }); setIntentado(false); setFalla(null); } }, [producto]);
  const errores = {
    cantidad: errorCantidad(v.cantidad) && t(errorCantidad(v.cantidad)),
    motivo: !v.motivo.trim() ? t('errores.obligatorio') : !SIN_HTML.test(v.motivo) ? t('errores.sinHtml') : null,
    hacia: v.desde === v.hacia ? t('inventario.estadosIguales') : null,
  };
  const enviar = async () => {
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setEnviando(true); setFalla(null);
    try {
      await api.inventario.cambiarEstado({ productoId: producto.productoId, desde: v.desde, hacia: v.hacia, cantidad: numero(v.cantidad),
        motivo: v.motivo.trim(), ...(v.idLote ? { idLote: Number(v.idLote) } : {}) });
      onHecho();
    } catch (e) { setFalla(mensajeDeError(t, e)); }
    finally { setEnviando(false); }
  };
  const estados = ['DISPONIBLE', 'RESERVADO', 'DANADO'];
  return (
    <Dialog open={Boolean(producto)} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="sm" aria-labelledby="cambio-estado-titulo">
      <DialogTitle id="cambio-estado-titulo">{t('inventario.cambiarEstadoDe', { producto: producto?.producto })}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <Typography variant="body2" color="text.secondary" gutterBottom>
          {estados.map((e) => `${t(`inventario.estados.${e}`)}: ${cantidad(producto?.[{ DISPONIBLE: 'disponible', RESERVADO: 'reservado', DANADO: 'danado' }[e]])}`).join(' · ')}
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2}>
          <TextField select label={t('inventario.desde')} value={v.desde} onChange={(e) => setV({ ...v, desde: e.target.value })} margin="dense" fullWidth>
            {estados.map((e) => <MenuItem key={e} value={e}>{t(`inventario.estados.${e}`)}</MenuItem>)}
          </TextField>
          <TextField select label={t('inventario.hacia')} value={v.hacia} onChange={(e) => setV({ ...v, hacia: e.target.value })} margin="dense" fullWidth
            error={intentado && Boolean(errores.hacia)} helperText={intentado ? errores.hacia : null}>
            {estados.map((e) => <MenuItem key={e} value={e}>{t(`inventario.estados.${e}`)}</MenuItem>)}
          </TextField>
        </Stack>
        {producto?.tipoRastreo === 'LOTE' && (
          <TextField select label={t('inventario.lote')} value={v.idLote} onChange={(e) => setV({ ...v, idLote: e.target.value })} margin="dense" fullWidth>
            <MenuItem value="">{t('inventario.sinLoteEspecifico')}</MenuItem>
            {(lotes.datos || []).map((l) => <MenuItem key={l.id} value={l.id}>{`${l.codigo} · ${cantidad(l.disponible)} / ${cantidad(l.danado)}`}</MenuItem>)}
          </TextField>)}
        <TextField label={t('movimiento.cantidad')} value={v.cantidad} onChange={(e) => setV({ ...v, cantidad: e.target.value })} required margin="dense" fullWidth
          inputProps={{ inputMode: 'decimal' }} error={intentado && Boolean(errores.cantidad)} helperText={intentado ? errores.cantidad : null} />
        <TextField label={t('comun.motivo')} value={v.motivo} onChange={(e) => setV({ ...v, motivo: e.target.value.slice(0, 255) })} required margin="dense" fullWidth multiline
          error={intentado && Boolean(errores.motivo)} helperText={intentado ? errores.motivo : null} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando} startIcon={enviando ? <CircularProgress size={16} color="inherit" /> : null}>{t('comun.confirmar')}</Button>
      </DialogActions>
    </Dialog>
  );
}

/** Nueva reserva de inventario para un cliente. */
export function ReservaDialog({ abierto, onCerrar, onHecha }) {
  const { t } = useTranslation();
  const [producto, setProducto] = useState(null);
  const [cliente, setCliente] = useState(null);
  const [v, setV] = useState({ cantidad: '', fechaLimite: '', idPedido: '' });
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  useEffect(() => { if (abierto) { setProducto(null); setCliente(null); setV({ cantidad: '', fechaLimite: '', idPedido: '' }); setIntentado(false); setFalla(null); } }, [abierto]);
  const errores = {
    producto: !producto ? t('errores.obligatorio') : null,
    cliente: !cliente ? t('errores.obligatorio') : null,
    cantidad: errorCantidad(v.cantidad) && t(errorCantidad(v.cantidad)),
    fechaLimite: !v.fechaLimite ? t('errores.obligatorio') : v.fechaLimite < hoyIso() ? t('errores.fechaFutura') : null,
    idPedido: !v.idPedido.trim() ? t('errores.obligatorio') : !SIN_HTML.test(v.idPedido) ? t('errores.sinHtml') : null,
  };
  const enviar = async () => {
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setEnviando(true); setFalla(null);
    try {
      await api.inventario.reservar({ productoId: producto.id, clienteId: cliente.id, cantidad: numero(v.cantidad), fechaLimite: v.fechaLimite, idPedido: v.idPedido.trim() });
      onHecha();
    } catch (e) { setFalla(mensajeDeError(t, e)); }
    finally { setEnviando(false); }
  };
  const e = (k) => (intentado ? errores[k] : null);
  return (
    <Dialog open={abierto} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="sm" aria-labelledby="reserva-titulo">
      <DialogTitle id="reserva-titulo">{t('reserva.nueva')}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <TerceroSelector id="reserva-cliente" rol="cliente" valor={cliente} onCambio={setCliente} requerido error={e('cliente')} autoFocus />
        <ProductoSelector id="reserva-producto" valor={producto} onCambio={setProducto} requerido error={e('producto')} />
        <TextField label={t('movimiento.cantidad')} value={v.cantidad} onChange={(x) => setV({ ...v, cantidad: x.target.value })} required margin="dense" fullWidth
          inputProps={{ inputMode: 'decimal' }} error={Boolean(e('cantidad'))} helperText={e('cantidad')} />
        <TextField type="date" label={t('reserva.fechaLimite')} value={v.fechaLimite} onChange={(x) => setV({ ...v, fechaLimite: x.target.value })} required margin="dense" fullWidth
          InputLabelProps={{ shrink: true }} inputProps={{ min: hoyIso() }} error={Boolean(e('fechaLimite'))} helperText={e('fechaLimite')} />
        <TextField label={t('reserva.pedido')} value={v.idPedido} onChange={(x) => setV({ ...v, idPedido: x.target.value.slice(0, 50) })} required margin="dense" fullWidth
          error={Boolean(e('idPedido'))} helperText={e('idPedido')} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando}>{t('reserva.crear')}</Button>
      </DialogActions>
    </Dialog>
  );
}
