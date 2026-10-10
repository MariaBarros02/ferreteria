/*
 * Nombre: TercerosPage.jsx
 * Descripción: CRUD de terceros (clientes/proveedores): campos condicionales por tipo de persona (RN-06) con Formik + Yup.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useCallback, useEffect, useState } from 'react';
import { Box, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, FormHelperText,
  IconButton, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material';
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
import { terceroSchema } from '../validation/schemas';
import { mensajeDeError, traducirValidacion } from '../utils/mensajes';

const VACIO = { tipoPersona: 'NATURAL', tipoIdentificacion: 'CC', numeroIdentificacion: '', primerNombre: '', segundoNombre: '', primerApellido: '',
  segundoApellido: '', razonSocial: '', direccion: '', ciudad: '', telefono: '', correo: '', cliente: true, proveedor: false };

/**
 * Nombre a mostrar de un tercero.
 * @param {object} t - Tercero.
 * @returns {string} Razón social o nombre completo.
 */
export const nombreTercero = (t) => (t.tipoPersona === 'JURIDICA' ? t.razonSocial : [t.primerNombre, t.segundoNombre, t.primerApellido, t.segundoApellido].filter(Boolean).join(' '));

/**
 * Página de gestión de terceros.
 * @returns {JSX.Element} Página.
 */
export default function TercerosPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const puedeEscribir = ['Compras', 'Ventas', 'Administrador'].includes(rol);
  const puedeEliminar = rol === 'Administrador';
  const [datos, setDatos] = useState({ contenido: [], totalElementos: 0 });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(10);
  const [q, setQ] = useState('');
  const [cargando, setCargando] = useState(false);
  const [editando, setEditando] = useState(null);
  const [aEliminar, setAEliminar] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    try { setDatos(await api.terceros.listar({ q, pagina, tamano })); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setCargando(false); }
  }, [q, pagina, tamano, notificar, t]);
  useEffect(() => { cargar(); }, [cargar]);

  const guardar = async (v, { setSubmitting }) => {
    try {
      const natural = v.tipoPersona === 'NATURAL';
      const limpio = (s) => (s && s.trim() ? s.trim() : null);
      const cuerpo = { ...v, numeroIdentificacion: v.numeroIdentificacion.trim(),
        primerNombre: natural ? limpio(v.primerNombre) : null, segundoNombre: natural ? limpio(v.segundoNombre) : null,
        primerApellido: natural ? limpio(v.primerApellido) : null, segundoApellido: natural ? limpio(v.segundoApellido) : null,
        razonSocial: natural ? null : limpio(v.razonSocial), direccion: v.direccion.trim(), ciudad: v.ciudad.trim(),
        telefono: v.telefono.trim(), correo: v.correo.trim() };
      delete cuerpo.id;
      if (editando?.id) await api.terceros.actualizar(editando.id, cuerpo); else await api.terceros.crear(cuerpo);
      notificar(t('comun.guardado')); setEditando(null); cargar();
    } catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setSubmitting(false); }
  };
  const eliminar = async () => {
    try { await api.terceros.eliminar(aEliminar.id); notificar(t('comun.eliminado')); setAEliminar(null); cargar(); }
    catch (e) { setAEliminar(null); notificar(mensajeDeError(t, e), 'error'); }
  };

  return (
    <section aria-labelledby="terceros-titulo">
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} mb={2}>
        <Typography id="terceros-titulo" variant="h4" component="h1">{t('tercero.titulo')}</Typography>
        {puedeEscribir && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditando({})}>{t('tercero.nuevo')}</Button>}
      </Stack>
      <TextField type="search" label={t('comun.buscar')} value={q} fullWidth sx={{ mb: 2 }}
        onChange={(e) => { setQ(e.target.value.slice(0, 100)); setPagina(0); }} />
      <Paper>
        <TableContainer>
          <Table aria-label={t('tercero.titulo')} size="small">
            <TableHead><TableRow>
              <TableCell>ID</TableCell><TableCell>{t('tercero.nombre')}</TableCell><TableCell>{t('tercero.numeroIdentificacion')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('tercero.ciudad')}</TableCell>
              <TableCell>{t('tercero.roles')}</TableCell>
              {puedeEscribir && <TableCell align="right">{t('comun.acciones')}</TableCell>}
            </TableRow></TableHead>
            <TableBody>
              {cargando && <TableRow><TableCell colSpan={6} align="center"><CircularProgress size={24} aria-label={t('comun.cargando')} /></TableCell></TableRow>}
              {!cargando && datos.contenido.length === 0 && <TableRow><TableCell colSpan={6} align="center">{t('comun.sinResultados')}</TableCell></TableRow>}
              {datos.contenido.map((x) => (
                <TableRow key={x.id} hover>
                  <TableCell>{x.id}</TableCell><TableCell>{nombreTercero(x)}</TableCell><TableCell>{x.tipoIdentificacion} {x.numeroIdentificacion}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{x.ciudad}</TableCell>
                  <TableCell>{x.cliente && <Chip size="small" label={t('tercero.cliente')} sx={{ mr: 0.5 }} />}{x.proveedor && <Chip size="small" label={t('tercero.proveedor')} />}</TableCell>
                  {puedeEscribir && <TableCell align="right">
                    <IconButton aria-label={`${t('comun.editar')} ${nombreTercero(x)}`} onClick={() => setEditando(x)}><EditIcon /></IconButton>
                    {puedeEliminar && <IconButton aria-label={`${t('comun.eliminar')} ${nombreTercero(x)}`} onClick={() => setAEliminar(x)}><DeleteIcon /></IconButton>}
                  </TableCell>}
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos.totalElementos} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, p) => setPagina(p)}
          onRowsPerPageChange={(e) => { setTamano(Number(e.target.value)); setPagina(0); }} />
      </Paper>

      <Dialog open={editando !== null} onClose={() => setEditando(null)} fullWidth maxWidth="sm" aria-labelledby="tercero-form-titulo">
        <DialogTitle id="tercero-form-titulo">{editando?.id ? t('tercero.editar') : t('tercero.nuevo')}</DialogTitle>
        {editando !== null && (
          <Formik initialValues={editando.id ? Object.fromEntries(Object.entries({ ...VACIO, ...editando }).map(([k, val]) => [k, val ?? (typeof VACIO[k] === 'boolean' ? false : '')])) : VACIO}
            validationSchema={terceroSchema} onSubmit={guardar}>
            {({ values, errors, touched, handleChange, isSubmitting }) => (
              <Form noValidate>
                <DialogContent>
                  <FormTextField name="tipoPersona" label={t('tercero.tipoPersona')} options={[{ value: 'NATURAL', label: t('tercero.natural') }, { value: 'JURIDICA', label: t('tercero.juridica') }]} />
                  <FormTextField name="tipoIdentificacion" label={t('tercero.tipoIdentificacion')} options={['CC', 'NIT', 'CE', 'PASAPORTE'].map((v) => ({ value: v, label: v }))} />
                  <FormTextField name="numeroIdentificacion" label={t('tercero.numeroIdentificacion')} required />
                  {values.tipoPersona === 'NATURAL' ? (
                    <>
                      <FormTextField name="primerNombre" label={t('tercero.primerNombre')} required />
                      <FormTextField name="segundoNombre" label={t('tercero.segundoNombre')} />
                      <FormTextField name="primerApellido" label={t('tercero.primerApellido')} required />
                      <FormTextField name="segundoApellido" label={t('tercero.segundoApellido')} />
                    </>
                  ) : <FormTextField name="razonSocial" label={t('tercero.razonSocial')} required />}
                  <FormTextField name="direccion" label={t('tercero.direccion')} required autoComplete="street-address" />
                  <FormTextField name="ciudad" label={t('tercero.ciudad')} required />
                  <FormTextField name="telefono" label={t('tercero.telefono')} required type="tel" autoComplete="tel" />
                  <FormTextField name="correo" label={t('tercero.correo')} required type="email" autoComplete="email" />
                  <Box>
                    <FormControlLabel control={<Checkbox name="cliente" checked={values.cliente} onChange={handleChange} />} label={t('tercero.cliente')} />
                    <FormControlLabel control={<Checkbox name="proveedor" checked={values.proveedor} onChange={handleChange} />} label={t('tercero.proveedor')} />
                    {touched.proveedor && errors.proveedor && <FormHelperText error role="alert">{traducirValidacion(t, errors.proveedor)}</FormHelperText>}
                  </Box>
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => setEditando(null)}>{t('comun.cancelar')}</Button>
                  <Button type="submit" variant="contained" disabled={isSubmitting}>{t('comun.guardar')}</Button>
                </DialogActions>
              </Form>)}
          </Formik>)}
      </Dialog>
      <ConfirmDialog abierto={Boolean(aEliminar)} titulo={`${t('comun.eliminar')}: ${aEliminar ? nombreTercero(aEliminar) : ''}`} mensaje={t('comun.confirmarEliminar')}
        onConfirmar={eliminar} onCancelar={() => setAEliminar(null)} />
    </section>
  );
}
