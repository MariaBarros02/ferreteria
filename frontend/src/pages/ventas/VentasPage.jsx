/*
 * Nombre: VentasPage.jsx
 * Descripción: Listado de ventas con filtros (número/factura, fechas, estado), paginación, exportación a Excel y acceso al detalle y a la nueva venta (RF-04, RF-18).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 2.0.0
 */
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Button, Chip, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TextField } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import BotonDescarga from '../../components/BotonDescarga';
import EstadoChip from '../../components/EstadoChip';
import { CampoFecha, Filtros } from '../../components/Filtros';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { fechaHora, moneda } from '../../utils/formato';
import { puede } from '../../utils/permisos';

export default function VentasPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const navegar = useNavigate();
  const [f, setF] = useState({ q: '', desde: '', hasta: '', estado: '' });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(20);
  const cambiar = (k) => (v) => { setF((x) => ({ ...x, [k]: v })); setPagina(0); };
  const params = { ...Object.fromEntries(Object.entries(f).filter(([, v]) => v)), pagina, tamano };
  const { datos, cargando, error, recargar } = useCarga(() => api.ventas.listar(params), [JSON.stringify(params)]);
  const filas = datos?.contenido || [];

  return (
    <section aria-labelledby="ventas-titulo">
      <PageHeader id="ventas-titulo" titulo={t('venta.titulo')} descripcion={t('venta.descripcion')}
        acciones={<>
          <BotonDescarga obtener={() => api.exportar('ventas', { desde: f.desde || undefined, hasta: f.hasta || undefined })} />
          {puede(rol, 'ventas.registrar') && <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/ventas/nueva">{t('venta.nueva')}</Button>}
        </>} />
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField size="small" type="search" label={t('venta.buscarNumero')} value={f.q} onChange={(e) => cambiar('q')(e.target.value.slice(0, 30))} />
        <CampoFecha label={t('comun.desde')} value={f.desde} onChange={cambiar('desde')} />
        <CampoFecha label={t('comun.hasta')} value={f.hasta} onChange={cambiar('hasta')} />
        <TextField size="small" select label={t('comun.estado')} value={f.estado} onChange={(e) => cambiar('estado')(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="">{t('comun.todos')}</MenuItem>
          <MenuItem value="ACTIVO">{t('estados.ACTIVO')}</MenuItem>
          <MenuItem value="ANULADO">{t('estados.ANULADO')}</MenuItem>
        </TextField>
      </Filtros>
      <Paper>
        <TableContainer>
          <Table aria-label={t('venta.titulo')} size="small">
            <TableHead><TableRow>
              <TableCell>{t('venta.numero')}</TableCell>
              <TableCell>{t('venta.fecha')}</TableCell>
              <TableCell>{t('venta.cliente')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('venta.factura')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('venta.vendedor')}</TableCell>
              <TableCell>{t('comun.estado')}</TableCell>
              <TableCell align="right">{t('venta.total')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={7} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((v) => (
                <TableRow key={v.id} hover sx={{ cursor: 'pointer' }} onClick={() => navegar(`/ventas/${v.id}`)}>
                  <TableCell><RouterLink to={`/ventas/${v.id}`} onClick={(e) => e.stopPropagation()}>{v.numeroInterno}</RouterLink></TableCell>
                  <TableCell>{fechaHora(v.fecha)}</TableCell>
                  <TableCell>{v.cliente}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{v.numeracionDian}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{v.usuario || '—'}</TableCell>
                  <TableCell>
                    <EstadoChip estado={v.estado} />
                    {v.tieneDevoluciones && <Chip size="small" variant="outlined" sx={{ ml: 0.5 }} label={t('venta.conDevoluciones')} />}
                  </TableCell>
                  <TableCell align="right">{moneda(v.total)}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, p) => setPagina(p)}
          onRowsPerPageChange={(e) => { setTamano(Number(e.target.value)); setPagina(0); }} />
      </Paper>
    </section>
  );
}
