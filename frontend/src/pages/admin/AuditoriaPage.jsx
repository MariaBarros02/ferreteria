/*
 * Nombre: AuditoriaPage.jsx
 * Descripción: Consulta de auditoría (RF-20, RNF-03): eventos funcionales (quién, cuándo, desde dónde, qué) con filtros, paginación y detalle; movimientos de inventario con filtros y exportación a Excel. Solo lectura: los registros son inmutables.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Stack, Tab, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import BotonDescarga from '../../components/BotonDescarga';
import { CampoFecha, Filtros } from '../../components/Filtros';
import PageHeader from '../../components/PageHeader';
import ProductoSelector from '../../components/ProductoSelector';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { cantidad, fechaHora } from '../../utils/formato';
import { puede } from '../../utils/permisos';
import { TIPOS_MOVIMIENTO } from '../inventario/InventarioDialogs';

const limpiar = (o) => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== '' && v !== null && v !== undefined));

function Eventos() {
  const { t } = useTranslation();
  const [f, setF] = useState({ desde: '', hasta: '', usuario: '', modulo: '', accion: '', entidad: '', q: '' });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(20);
  const [detalle, setDetalle] = useState(null);
  const cambiar = (k) => (v) => { setF((x) => ({ ...x, [k]: v })); setPagina(0); };
  const catalogo = useCarga(() => api.auditoria.catalogo(), []);
  const params = { ...limpiar(f), pagina, tamano };
  const { datos, cargando, error, recargar } = useCarga(() => api.auditoria.eventos(params), [JSON.stringify(params)]);
  const filas = datos?.contenido || [];
  return (
    <>
      <Filtros etiqueta={t('comun.filtros')}>
        <CampoFecha label={t('comun.desde')} value={f.desde} onChange={cambiar('desde')} />
        <CampoFecha label={t('comun.hasta')} value={f.hasta} onChange={cambiar('hasta')} />
        <TextField size="small" label={t('auditoria.usuario')} value={f.usuario} onChange={(e) => cambiar('usuario')(e.target.value.slice(0, 150))} />
        <TextField select size="small" label={t('auditoria.modulo')} value={f.modulo} onChange={(e) => cambiar('modulo')(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="">{t('comun.todos')}</MenuItem>
          {(catalogo.datos?.modulos || []).map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={t('auditoria.accion')} value={f.accion} onChange={(e) => cambiar('accion')(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="">{t('comun.todas')}</MenuItem>
          {(catalogo.datos?.acciones || []).map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}
        </TextField>
        <TextField size="small" label={t('auditoria.entidad')} value={f.entidad} onChange={(e) => cambiar('entidad')(e.target.value.slice(0, 40))} />
        <TextField size="small" type="search" label={t('auditoria.texto')} value={f.q} onChange={(e) => cambiar('q')(e.target.value.slice(0, 100))} />
      </Filtros>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('auditoria.eventos')}>
            <TableHead><TableRow>
              <TableCell>{t('auditoria.fecha')}</TableCell><TableCell>{t('auditoria.usuario')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>IP</TableCell>
              <TableCell>{t('auditoria.modulo')}</TableCell><TableCell>{t('auditoria.accion')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('auditoria.entidad')}</TableCell>
              <TableCell>{t('auditoria.descripcion')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={7} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((e) => (
                <TableRow key={e.id} hover sx={{ cursor: 'pointer' }} onClick={() => setDetalle(e)} tabIndex={0}
                  onKeyDown={(ev) => { if (ev.key === 'Enter') setDetalle(e); }} aria-label={`${t('auditoria.verDetalle')} ${e.id}`}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{fechaHora(e.fechaHora)}</TableCell>
                  <TableCell>{e.correoUsuario || '—'}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{e.ip}</TableCell>
                  <TableCell>{e.modulo}</TableCell><TableCell>{e.accion}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{e.entidad} {e.idEntidad}</TableCell>
                  <TableCell>{e.descripcion}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[20, 50, 100]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, p) => setPagina(p)} onRowsPerPageChange={(e) => { setTamano(Number(e.target.value)); setPagina(0); }} />
      </Paper>
      <Dialog open={Boolean(detalle)} onClose={() => setDetalle(null)} fullWidth maxWidth="md" aria-labelledby="evento-titulo">
        <DialogTitle id="evento-titulo">{t('auditoria.evento', { id: detalle?.id })}</DialogTitle>
        <DialogContent>
          {detalle && (
            <Stack gap={1}>
              <Typography variant="body2"><strong>{t('auditoria.fecha')}:</strong> {fechaHora(detalle.fechaHora)}</Typography>
              <Typography variant="body2"><strong>{t('auditoria.usuario')}:</strong> {detalle.correoUsuario || '—'} (#{detalle.idUsuario ?? '—'}) · IP {detalle.ip || '—'}</Typography>
              <Typography variant="body2"><strong>{t('auditoria.modulo')}:</strong> {detalle.modulo} · <strong>{t('auditoria.accion')}:</strong> {detalle.accion}</Typography>
              <Typography variant="body2"><strong>{t('auditoria.entidad')}:</strong> {detalle.entidad} {detalle.idEntidad}</Typography>
              {detalle.idOperacion && <Typography variant="body2"><strong>{t('auditoria.operacion')}:</strong> {detalle.idOperacion}</Typography>}
              <Typography variant="body2">{detalle.descripcion}</Typography>
              <Box component="pre" sx={{ bgcolor: 'action.hover', p: 2, borderRadius: 1, overflowX: 'auto', fontSize: 13, m: 0 }}>
                {JSON.stringify(detalle.detalle ?? {}, null, 2)}
              </Box>
            </Stack>)}
        </DialogContent>
        <DialogActions><Button onClick={() => setDetalle(null)}>{t('comun.cerrar')}</Button></DialogActions>
      </Dialog>
    </>
  );
}

function Movimientos() {
  const { t } = useTranslation();
  const [producto, setProducto] = useState(null);
  const [f, setF] = useState({ tipo: '', documento: '', desde: '', hasta: '' });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(20);
  const cambiar = (k) => (v) => { setF((x) => ({ ...x, [k]: v })); setPagina(0); };
  const params = { ...limpiar({ ...f, idProducto: producto?.id }), pagina, tamano };
  const { datos, cargando, error, recargar } = useCarga(() => api.auditoria.movimientos(params), [JSON.stringify(params)]);
  const filas = datos?.contenido || [];
  return (
    <>
      <Filtros etiqueta={t('comun.filtros')}>
        <Box sx={{ minWidth: 260, flexGrow: 1 }}><ProductoSelector id="auditoria-producto" valor={producto} onCambio={(p) => { setProducto(p); setPagina(0); }} mostrarPrecio={false} /></Box>
        <TextField select size="small" label={t('movimiento.tipo')} value={f.tipo} onChange={(e) => cambiar('tipo')(e.target.value)} sx={{ minWidth: 190 }}>
          <MenuItem value="">{t('comun.todos')}</MenuItem>
          {TIPOS_MOVIMIENTO.map((x) => <MenuItem key={x} value={x}>{t(`movimiento.tipos.${x}`)}</MenuItem>)}
        </TextField>
        <TextField size="small" label={t('movimiento.documento')} value={f.documento} onChange={(e) => cambiar('documento')(e.target.value.slice(0, 100))} />
        <CampoFecha label={t('comun.desde')} value={f.desde} onChange={cambiar('desde')} />
        <CampoFecha label={t('comun.hasta')} value={f.hasta} onChange={cambiar('hasta')} />
        <BotonDescarga obtener={() => api.exportar('movimientos', limpiar({ productoId: producto?.id, tipo: f.tipo, desde: f.desde, hasta: f.hasta }))} />
      </Filtros>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('auditoria.movimientos')}>
            <TableHead><TableRow>
              <TableCell>{t('movimiento.fecha')}</TableCell><TableCell>{t('movimiento.tipo')}</TableCell><TableCell>{t('venta.producto')}</TableCell>
              <TableCell>{t('movimiento.estado')}</TableCell><TableCell align="right">{t('movimiento.cantidad')}</TableCell>
              <TableCell align="right">{t('movimiento.saldoAnterior')}</TableCell><TableCell align="right">{t('movimiento.saldoNuevo')}</TableCell>
              <TableCell>{t('movimiento.documento')}</TableCell><TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('comun.motivo')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{t('auditoria.usuario')} / IP</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={10} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((m) => (
                <TableRow key={m.id}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{fechaHora(m.fechaHora)}</TableCell>
                  <TableCell>{t(`movimiento.tipos.${m.tipo}`)}{m.idMovimientoOriginal ? <Typography variant="caption" component="div">↩ #{m.idMovimientoOriginal}</Typography> : null}</TableCell>
                  <TableCell>#{m.idProducto}</TableCell>
                  <TableCell>{t(`inventario.estados.${m.estadoInventario}`)}{m.idLote ? ` · ${t('inventario.lote')} ${m.idLote}` : ''}</TableCell>
                  <TableCell align="right">{cantidad(m.cantidad)}</TableCell>
                  <TableCell align="right">{cantidad(m.valorAnterior)}</TableCell>
                  <TableCell align="right">{cantidad(m.valorNuevo)}</TableCell>
                  <TableCell>{m.documentoRelacionado}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{m.motivo}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>#{m.idUsuario} · {m.ip}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[20, 50, 100]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, p) => setPagina(p)} onRowsPerPageChange={(e) => { setTamano(Number(e.target.value)); setPagina(0); }} />
      </Paper>
    </>
  );
}

export default function AuditoriaPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const [pestana, setPestana] = useState('eventos');
  return (
    <section aria-labelledby="auditoria-titulo">
      <PageHeader id="auditoria-titulo" titulo={t('auditoria.titulo')} descripcion={t('auditoria.descripcion')} />
      <Tabs value={pestana} onChange={(_, v) => setPestana(v)} aria-label={t('auditoria.titulo')} sx={{ mb: 2 }}>
        <Tab value="eventos" label={t('auditoria.eventos')} />
        {puede(rol, 'exportar.movimientos') && <Tab value="movimientos" label={t('auditoria.movimientos')} />}
      </Tabs>
      {pestana === 'eventos' ? <Eventos /> : <Movimientos />}
    </section>
  );
}
