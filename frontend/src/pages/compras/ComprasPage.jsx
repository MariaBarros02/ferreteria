/*
 * Nombre: ComprasPage.jsx
 * Descripción: Listado de órdenes de compra con filtros (número, fechas, estado), estado de recepción y acceso al detalle y a la nueva compra (RF-03, RF-17).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Button, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TextField } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import EstadoChip from '../../components/EstadoChip';
import { CampoFecha, Filtros } from '../../components/Filtros';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { fechaHora, moneda } from '../../utils/formato';
import { puede } from '../../utils/permisos';

export default function ComprasPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const navegar = useNavigate();
  const [f, setF] = useState({ q: '', desde: '', hasta: '', estado: '' });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(20);
  const cambiar = (k) => (v) => { setF((x) => ({ ...x, [k]: v })); setPagina(0); };
  const params = { ...Object.fromEntries(Object.entries(f).filter(([, v]) => v)), pagina, tamano };
  const { datos, cargando, error, recargar } = useCarga(() => api.compras.listar(params), [JSON.stringify(params)]);
  const filas = datos?.contenido || [];

  return (
    <section aria-labelledby="compras-titulo">
      <PageHeader id="compras-titulo" titulo={t('compra.titulo')} descripcion={t('compra.descripcion')}
        acciones={puede(rol, 'compras.registrar') && <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/compras/nueva">{t('compra.nueva')}</Button>} />
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField size="small" type="search" label={t('compra.buscarNumero')} value={f.q} onChange={(e) => cambiar('q')(e.target.value.slice(0, 30))} />
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
          <Table size="small" aria-label={t('compra.titulo')}>
            <TableHead><TableRow>
              <TableCell>{t('compra.numero')}</TableCell>
              <TableCell>{t('compra.fecha')}</TableCell>
              <TableCell>{t('compra.proveedor')}</TableCell>
              <TableCell>{t('comun.estado')}</TableCell>
              <TableCell>{t('compra.recepcion')}</TableCell>
              <TableCell align="right" sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('compra.lineas')}</TableCell>
              <TableCell align="right">{t('compra.total')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={7} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((c) => (
                <TableRow key={c.id} hover sx={{ cursor: 'pointer' }} onClick={() => navegar(`/compras/${c.id}`)}>
                  <TableCell><RouterLink to={`/compras/${c.id}`} onClick={(e) => e.stopPropagation()}>{c.numero}</RouterLink></TableCell>
                  <TableCell>{fechaHora(c.fecha)}</TableCell>
                  <TableCell>{c.proveedor}</TableCell>
                  <TableCell><EstadoChip estado={c.estado} /></TableCell>
                  <TableCell><EstadoChip estado={c.estadoRecepcion} /></TableCell>
                  <TableCell align="right" sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{c.lineas}</TableCell>
                  <TableCell align="right">{moneda(c.total)}</TableCell>
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
