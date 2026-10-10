/*
 * Nombre: ProductosPage.jsx
 * Descripción: CRUD de productos (JSON ↔ API REST): tabla paginada, búsqueda, formulario Formik+Yup y confirmación de borrado.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useCallback, useEffect, useState } from 'react';
import { Box, Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton,
  Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { Form, Formik } from 'formik';
import { useTranslation } from 'react-i18next';
import { api } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import ConfirmDialog from '../components/ConfirmDialog';
import FormTextField from '../components/FormTextField';
import { useNotify } from '../components/Notifier';
import { productoSchema } from '../validation/schemas';
import { mensajeDeError } from '../utils/mensajes';

const VACIO = { nombre: '', marca: '', presentacion: '', codigoBarras: '', unidadBase: 'unidad', tipoRastreo: 'NINGUNO', tieneVencimiento: false, tieneGarantia: false };

/**
 * Página de gestión de productos.
 * @returns {JSX.Element} Página.
 */
export default function ProductosPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const puedeEscribir = ['Inventario', 'Administrador'].includes(rol);
  const puedeEliminar = rol === 'Administrador';

  const [datos, setDatos] = useState({ contenido: [], totalElementos: 0 });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(10);
  const [q, setQ] = useState('');
  const [cargando, setCargando] = useState(false);
  const [editando, setEditando] = useState(null); // null = cerrado, {} = nuevo, objeto = edición
  const [aEliminar, setAEliminar] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    try { setDatos(await api.productos.listar({ q, pagina, tamano })); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setCargando(false); }
  }, [q, pagina, tamano, notificar, t]);

  useEffect(() => { cargar(); }, [cargar]);

  const guardar = async (valores, { setSubmitting }) => {
    try {
      const cuerpo = { ...valores, nombre: valores.nombre.trim(), marca: valores.marca.trim(), presentacion: valores.presentacion.trim(),
        codigoBarras: valores.codigoBarras.trim(), unidadBase: valores.unidadBase.trim() };
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

  return (
    <section aria-labelledby="productos-titulo">
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} mb={2}>
        <Typography id="productos-titulo" variant="h4" component="h1">{t('producto.titulo')}</Typography>
        {puedeEscribir && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditando({})}>{t('producto.nuevo')}</Button>}
      </Stack>
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
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('producto.tipoRastreo')}</TableCell>
                {puedeEscribir && <TableCell align="right">{t('comun.acciones')}</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {cargando && <TableRow><TableCell colSpan={7} align="center"><CircularProgress size={24} aria-label={t('comun.cargando')} /></TableCell></TableRow>}
              {!cargando && datos.contenido.length === 0 && <TableRow><TableCell colSpan={7} align="center">{t('comun.sinResultados')}</TableCell></TableRow>}
              {datos.contenido.map((p) => (
                <TableRow key={p.id} hover>
                  <TableCell>{p.id}</TableCell><TableCell>{p.nombre}</TableCell><TableCell>{p.marca}</TableCell><TableCell>{p.presentacion}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{p.codigoBarras}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t(`producto.rastreo.${p.tipoRastreo}`)}</TableCell>
                  {puedeEscribir && (
                    <TableCell align="right">
                      <IconButton aria-label={`${t('comun.editar')} ${p.nombre}`} onClick={() => setEditando(p)}><EditIcon /></IconButton>
                      {puedeEliminar && <IconButton aria-label={`${t('comun.eliminar')} ${p.nombre}`} onClick={() => setAEliminar(p)}><DeleteIcon /></IconButton>}
                    </TableCell>)}
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos.totalElementos} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, p) => setPagina(p)}
          onRowsPerPageChange={(e) => { setTamano(Number(e.target.value)); setPagina(0); }} />
      </Paper>

      <Dialog open={editando !== null} onClose={() => setEditando(null)} fullWidth maxWidth="sm" aria-labelledby="producto-form-titulo">
        <DialogTitle id="producto-form-titulo">{editando?.id ? t('producto.editar') : t('producto.nuevo')}</DialogTitle>
        {editando !== null && (
          <Formik initialValues={editando.id ? { ...VACIO, ...editando } : VACIO} validationSchema={productoSchema} onSubmit={guardar}>
            {({ values, handleChange, isSubmitting }) => (
              <Form noValidate>
                <DialogContent>
                  <FormTextField name="nombre" label={t('producto.nombre')} required autoFocus />
                  <FormTextField name="marca" label={t('producto.marca')} required />
                  <FormTextField name="presentacion" label={t('producto.presentacion')} required />
                  <FormTextField name="codigoBarras" label={t('producto.codigoBarras')} required inputProps={{ inputMode: 'numeric' }} />
                  <FormTextField name="unidadBase" label={t('producto.unidadBase')} required />
                  <FormTextField name="tipoRastreo" label={t('producto.tipoRastreo')}
                    options={['NINGUNO', 'LOTE', 'SERIE'].map((v) => ({ value: v, label: t(`producto.rastreo.${v}`) }))} />
                  <Box>
                    <FormControlLabel control={<Checkbox name="tieneVencimiento" checked={values.tieneVencimiento} onChange={handleChange} />} label={t('producto.tieneVencimiento')} />
                    <FormControlLabel control={<Checkbox name="tieneGarantia" checked={values.tieneGarantia} onChange={handleChange} />} label={t('producto.tieneGarantia')} />
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
    </section>
  );
}
