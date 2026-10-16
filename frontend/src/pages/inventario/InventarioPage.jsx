/*
 * Nombre: InventarioPage.jsx
 * Descripción: Inventario (RF-05): existencias por estado con alertas, costos según rol y exportación; alertas de vencidos, por vencer, agotados y stock bajo con baja de lotes vencidos; reservas con creación, cancelación y liberación de vencidas.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Card, CardContent, Grid, IconButton, MenuItem, Paper, Stack, Tab, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Tooltip, Typography } from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import AddIcon from '@mui/icons-material/Add';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import BotonDescarga from '../../components/BotonDescarga';
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import { Filtros } from '../../components/Filtros';
import MotivoDialog from '../../components/MotivoDialog';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { cantidad, fecha, fechaHora, moneda } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { CambioEstadoDialog, KardexDialog, ReservaDialog } from './InventarioDialogs';

function Existencias() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const costos = puede(rol, 'inventario.costos');
  const [q, setQ] = useState('');
  const [dias, setDias] = useState('');
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(20);
  const [kardex, setKardex] = useState(null);
  const [cambio, setCambio] = useState(null);
  const params = { q: q || undefined, dias: dias || undefined, pagina, tamano };
  const { datos, cargando, error, recargar } = useCarga(() => api.inventario.existencias(params), [JSON.stringify(params)]);
  const filas = datos?.contenido || [];
  const columnas = costos ? 11 : 9;
  return (
    <>
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField size="small" type="search" label={t('comun.buscar')} placeholder={t('producto.buscarPlaceholder')} value={q}
          onChange={(e) => { setQ(e.target.value.slice(0, 100)); setPagina(0); }} sx={{ flexGrow: 1 }} />
        <TextField size="small" label={t('inventario.diasUmbral')} value={dias} type="number" inputProps={{ min: 0, max: 365 }}
          onChange={(e) => { setDias(e.target.value); setPagina(0); }} sx={{ width: 180 }} />
        <BotonDescarga obtener={() => api.exportar('existencias')} />
      </Filtros>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('inventario.existencias')}>
            <TableHead><TableRow>
              <TableCell>{t('venta.producto')}</TableCell>
              <TableCell align="right">{t('inventario.estados.DISPONIBLE')}</TableCell>
              <TableCell align="right">{t('inventario.estados.RESERVADO')}</TableCell>
              <TableCell align="right">{t('inventario.estados.DANADO')}</TableCell>
              <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('inventario.porVencer')}</TableCell>
              <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('producto.stockMinimo')}</TableCell>
              <TableCell align="right" sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('inventario.precioVigente')}</TableCell>
              {costos && <TableCell align="right" sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{t('producto.costoPromedio')}</TableCell>}
              {costos && <TableCell align="right" sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{t('inventario.valor')}</TableCell>}
              <TableCell>{t('inventario.alertas')}</TableCell>
              <TableCell align="right">{t('comun.acciones')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={columnas} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((e) => (
                <TableRow key={e.productoId} hover>
                  <TableCell>{e.producto}<Typography variant="caption" color="text.secondary" component="div">{e.codigoBarras} · {e.unidadBase}{!e.activo ? ` · ${t('estados.INACTIVO')}` : ''}</Typography></TableCell>
                  <TableCell align="right">{cantidad(e.disponible)}</TableCell>
                  <TableCell align="right">{cantidad(e.reservado)}</TableCell>
                  <TableCell align="right">{cantidad(e.danado)}</TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{cantidad(e.porVencer)}</TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', md: 'table-cell' } }}>{cantidad(e.stockMinimo)}</TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{moneda(e.precioVigente)}</TableCell>
                  {costos && <TableCell align="right" sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{moneda(e.costoPromedio, 2)}</TableCell>}
                  {costos && <TableCell align="right" sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{moneda(e.valorInventario)}</TableCell>}
                  <TableCell><Stack direction="row" gap={0.5} flexWrap="wrap">{(e.alertas || []).map((a) => <EstadoChip key={a} estado={a} />)}</Stack></TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title={t('inventario.verKardex')}><IconButton aria-label={`${t('inventario.verKardex')} ${e.producto}`} onClick={() => setKardex(e)}><HistoryIcon /></IconButton></Tooltip>
                    {puede(rol, 'inventario.transicion') && (
                      <Tooltip title={t('inventario.cambiarEstado')}><IconButton aria-label={`${t('inventario.cambiarEstado')} ${e.producto}`} onClick={() => setCambio(e)}><SwapHorizIcon /></IconButton></Tooltip>)}
                  </TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[10, 20, 50, 100]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, p) => setPagina(p)}
          onRowsPerPageChange={(ev) => { setTamano(Number(ev.target.value)); setPagina(0); }} />
      </Paper>
      <KardexDialog producto={kardex} onCerrar={() => setKardex(null)} />
      <CambioEstadoDialog producto={cambio} onCerrar={() => setCambio(null)} onHecho={() => { setCambio(null); notificar(t('inventario.estadoCambiado')); recargar(); }} />
    </>
  );
}

function Alertas() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const [dias, setDias] = useState('');
  const [baja, setBaja] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const { datos: a, cargando, error, recargar } = useCarga(() => api.inventario.alertas(dias || undefined), [dias]);
  const darDeBaja = async () => {
    setEnviando(true);
    try { await api.inventario.bajaLote(baja.id); notificar(t('inventario.loteDadoDeBaja')); setBaja(null); recargar(); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setEnviando(false); }
  };
  if (error) return <Alert severity="error" role="alert" action={<Button onClick={recargar}>{t('comun.reintentar')}</Button>}>{mensajeDeError(t, error)}</Alert>;
  const tarjetas = [
    ['VENCIDO', a?.vencidos?.length], ['POR_VENCER', a?.porVencer?.length], ['AGOTADO', a?.agotados?.length], ['STOCK_BAJO', a?.stockBajo?.length],
  ];
  const tablaLotes = (lista, titulo, conBaja) => (
    <Paper sx={{ mb: 2 }}>
      <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{titulo}</Typography>
      <TableContainer>
        <Table size="small" aria-label={titulo}>
          <TableHead><TableRow>
            <TableCell>{t('venta.producto')}</TableCell><TableCell>{t('inventario.lote')}</TableCell><TableCell>{t('inventario.vencimiento')}</TableCell>
            <TableCell align="right">{t('inventario.diasParaVencer')}</TableCell><TableCell align="right">{t('inventario.estados.DISPONIBLE')}</TableCell>
            {conBaja && <TableCell align="right">{t('comun.acciones')}</TableCell>}
          </TableRow></TableHead>
          <TableBody>
            <TablaEstado columnas={conBaja ? 6 : 5} cargando={cargando && !a} vacio={!lista?.length} />
            {(lista || []).map((l) => (
              <TableRow key={l.id}>
                <TableCell>{l.producto}</TableCell><TableCell>{l.codigo}</TableCell><TableCell>{fecha(l.fechaVencimiento)}</TableCell>
                <TableCell align="right">{l.diasParaVencer}</TableCell><TableCell align="right">{cantidad(l.disponible)}</TableCell>
                {conBaja && <TableCell align="right">{puede(rol, 'inventario.transicion') && Number(l.disponible) + Number(l.danado || 0) > 0 && (
                  <Button size="small" color="error" onClick={() => setBaja(l)}>{t('inventario.darDeBaja')}</Button>)}</TableCell>}
              </TableRow>))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
  const tablaProductos = (lista, titulo) => (
    <Paper sx={{ mb: 2 }}>
      <Typography variant="h6" component="h2" sx={{ p: 2, pb: 0 }}>{titulo}</Typography>
      <TableContainer>
        <Table size="small" aria-label={titulo}>
          <TableHead><TableRow><TableCell>{t('venta.producto')}</TableCell><TableCell align="right">{t('inventario.estados.DISPONIBLE')}</TableCell><TableCell align="right">{t('producto.stockMinimo')}</TableCell></TableRow></TableHead>
          <TableBody>
            <TablaEstado columnas={3} cargando={cargando && !a} vacio={!lista?.length} />
            {(lista || []).map((e) => (
              <TableRow key={e.productoId}><TableCell>{e.producto}</TableCell><TableCell align="right">{cantidad(e.disponible)}</TableCell><TableCell align="right">{cantidad(e.stockMinimo)}</TableCell></TableRow>))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ sm: 'center' }} mb={2}>
        <TextField size="small" label={t('inventario.diasUmbral')} type="number" value={dias} onChange={(e) => setDias(e.target.value)} inputProps={{ min: 0, max: 365 }} />
        {a && <Typography variant="body2" color="text.secondary">{t('inventario.referencia', { fecha: fecha(a.fechaReferencia), dias: a.diasUmbral })}</Typography>}
      </Stack>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        {tarjetas.map(([k, n]) => (
          <Grid item xs={6} md={3} key={k}>
            <Card variant="outlined"><CardContent>
              <Typography variant="overline" component="p">{t(`estados.${k}`)}</Typography>
              <Typography variant="h4" component="p">{n ?? '—'}</Typography>
            </CardContent></Card>
          </Grid>))}
      </Grid>
      {tablaLotes(a?.vencidos, t('inventario.lotesVencidos'), true)}
      {tablaLotes(a?.porVencer, t('inventario.lotesPorVencer'), false)}
      {tablaProductos(a?.agotados, t('inventario.agotados'))}
      {tablaProductos(a?.stockBajo, t('inventario.stockBajo'))}
      <ConfirmDialog abierto={Boolean(baja)} titulo={t('inventario.darDeBajaTitulo', { lote: baja?.codigo })} mensaje={t('inventario.darDeBajaMensaje', { producto: baja?.producto })}
        textoConfirmar={t('inventario.darDeBaja')} cargando={enviando} onConfirmar={darDeBaja} onCancelar={() => setBaja(null)} />
    </>
  );
}

function Reservas() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const [estado, setEstado] = useState('ACTIVA');
  const [pagina, setPagina] = useState(0);
  const [nueva, setNueva] = useState(false);
  const [cancelar, setCancelar] = useState(null);
  const { datos, cargando, error, recargar } = useCarga(() => api.inventario.reservas({ estado: estado || undefined, pagina, tamano: 20 }), [estado, pagina]);
  const filas = datos?.contenido || [];
  const liberar = async () => {
    try { const r = await api.inventario.liberarVencidas(); notificar(t('reserva.liberadas', { n: r?.liberadas ?? 0 })); recargar(); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
  };
  const confirmarCancelacion = async (motivo) => {
    try { await api.inventario.cancelarReserva(cancelar.id, motivo); notificar(t('reserva.cancelada')); setCancelar(null); recargar(); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
  };
  return (
    <>
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField select size="small" label={t('comun.estado')} value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0); }} sx={{ minWidth: 220 }}>
          <MenuItem value="">{t('comun.todos')}</MenuItem>
          {['ACTIVA', 'LIBERADA_VENTA', 'LIBERADA_CANCELACION', 'LIBERADA_VENCIMIENTO'].map((x) => <MenuItem key={x} value={x}>{t(`estados.${x}`)}</MenuItem>)}
        </TextField>
        <Box sx={{ flexGrow: 1 }} />
        {puede(rol, 'inventario.transicion') && <Button onClick={liberar}>{t('reserva.liberarVencidas')}</Button>}
        {puede(rol, 'reservas.crear') && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setNueva(true)}>{t('reserva.nueva')}</Button>}
      </Filtros>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('inventario.reservas')}>
            <TableHead><TableRow>
              <TableCell>#</TableCell><TableCell>{t('venta.producto')}</TableCell><TableCell>{t('venta.cliente')}</TableCell>
              <TableCell>{t('reserva.pedido')}</TableCell><TableCell align="right">{t('movimiento.cantidad')}</TableCell>
              <TableCell>{t('reserva.fechaLimite')}</TableCell><TableCell>{t('comun.estado')}</TableCell><TableCell align="right">{t('comun.acciones')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={8} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>{r.id}</TableCell><TableCell>{r.producto}</TableCell><TableCell>{r.cliente}</TableCell><TableCell>{r.idPedido}</TableCell>
                  <TableCell align="right">{cantidad(r.cantidad)}</TableCell><TableCell>{fecha(r.fechaLimite)}</TableCell>
                  <TableCell><EstadoChip estado={r.estado} />{r.fechaLiberacion && <Typography variant="caption" component="div" color="text.secondary">{fechaHora(r.fechaLiberacion)} {r.motivoLiberacion}</Typography>}</TableCell>
                  <TableCell align="right">{r.estado === 'ACTIVA' && puede(rol, 'reservas.cancelar') && <Button size="small" color="error" onClick={() => setCancelar(r)}>{t('reserva.cancelar')}</Button>}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
          onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
      </Paper>
      <ReservaDialog abierto={nueva} onCerrar={() => setNueva(false)} onHecha={() => { setNueva(false); notificar(t('reserva.creada')); recargar(); }} />
      <MotivoDialog abierto={Boolean(cancelar)} titulo={t('reserva.cancelarTitulo', { id: cancelar?.id })} textoConfirmar={t('reserva.cancelar')}
        onConfirmar={confirmarCancelacion} onCancelar={() => setCancelar(null)} />
    </>
  );
}

export default function InventarioPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const [params, setParams] = useSearchParams();
  const pestanas = ['existencias', 'alertas', ...(puede(rol, 'reservas.ver') ? ['reservas'] : [])];
  const actual = pestanas.includes(params.get('vista')) ? params.get('vista') : 'existencias';
  return (
    <section aria-labelledby="inventario-titulo">
      <PageHeader id="inventario-titulo" titulo={t('inventario.titulo')} descripcion={t('inventario.descripcion')} />
      <Tabs value={actual} onChange={(_, v) => setParams({ vista: v })} aria-label={t('inventario.titulo')} sx={{ mb: 2 }} variant="scrollable" allowScrollButtonsMobile>
        {pestanas.map((p) => <Tab key={p} value={p} label={t(`inventario.${p}`)} />)}
      </Tabs>
      {actual === 'existencias' && <Existencias />}
      {actual === 'alertas' && <Alertas />}
      {actual === 'reservas' && <Reservas />}
    </section>
  );
}
