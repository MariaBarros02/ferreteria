/*
 * Nombre: DevolucionesPage.jsx
 * Descripción: Listado de devoluciones de clientes y a proveedores (RF-13) con filtros por tipo, documento y fechas, y acceso al registro de devoluciones de clientes.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Button, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import { CampoFecha, Filtros } from '../../components/Filtros';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { fechaHora, moneda } from '../../utils/formato';
import { puede } from '../../utils/permisos';

export default function DevolucionesPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const navegar = useNavigate();
  const [f, setF] = useState({ tipo: '', q: '', desde: '', hasta: '' });
  const [pagina, setPagina] = useState(0);
  const cambiar = (k) => (v) => { setF((x) => ({ ...x, [k]: v })); setPagina(0); };
  const params = { ...Object.fromEntries(Object.entries(f).filter(([, v]) => v)), pagina, tamano: 20 };
  const { datos, cargando, error, recargar } = useCarga(() => api.devoluciones.listar(params), [JSON.stringify(params)]);
  const filas = datos?.contenido || [];
  return (
    <section aria-labelledby="devoluciones-titulo">
      <PageHeader id="devoluciones-titulo" titulo={t('devolucion.titulo')} descripcion={t('devolucion.descripcion')}
        acciones={puede(rol, 'devoluciones.cliente') && <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/devoluciones/nueva">{t('devolucion.nuevaCliente')}</Button>} />
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField select size="small" label={t('devolucion.tipo')} value={f.tipo} onChange={(e) => cambiar('tipo')(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">{t('comun.todos')}</MenuItem>
          <MenuItem value="CLIENTE">{t('devolucion.tipos.CLIENTE')}</MenuItem>
          <MenuItem value="PROVEEDOR">{t('devolucion.tipos.PROVEEDOR')}</MenuItem>
        </TextField>
        <TextField size="small" type="search" label={t('devolucion.buscarNumero')} value={f.q} onChange={(e) => cambiar('q')(e.target.value.slice(0, 30))} />
        <CampoFecha label={t('comun.desde')} value={f.desde} onChange={cambiar('desde')} />
        <CampoFecha label={t('comun.hasta')} value={f.hasta} onChange={cambiar('hasta')} />
      </Filtros>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('devolucion.titulo')}>
            <TableHead><TableRow>
              <TableCell>{t('devolucion.numero')}</TableCell><TableCell>{t('devolucion.tipo')}</TableCell><TableCell>{t('devolucion.documentoOrigen')}</TableCell>
              <TableCell>{t('devolucion.tercero')}</TableCell><TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('venta.fecha')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('devolucion.tratamiento')}</TableCell><TableCell align="right">{t('venta.total')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={7} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((d) => (
                <TableRow key={d.id} hover sx={{ cursor: 'pointer' }} onClick={() => navegar(`/devoluciones/${d.id}`)}>
                  <TableCell><RouterLink to={`/devoluciones/${d.id}`} onClick={(e) => e.stopPropagation()}>{d.numero}</RouterLink></TableCell>
                  <TableCell>{t(`devolucion.tipos.${d.tipo}`)}</TableCell>
                  <TableCell>{d.ventaNumero || d.compraNumero}</TableCell>
                  <TableCell>{d.tercero}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{fechaHora(d.fecha)}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t(`devolucion.tratamientos.${d.tratamiento}`)}</TableCell>
                  <TableCell align="right">{moneda(d.total, 2)}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
          onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
      </Paper>
    </section>
  );
}
