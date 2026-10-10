/*
 * Nombre: VentasPage.jsx
 * Descripción: Registro y listado de ventas: formulario con líneas dinámicas (FieldArray), validación Yup y resultado con factura (RF-04).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useCallback, useEffect, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { FieldArray, Form, Formik } from 'formik';
import { useTranslation } from 'react-i18next';
import { api } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import FormTextField from '../components/FormTextField';
import { useNotify } from '../components/Notifier';
import { ventaSchema } from '../validation/schemas';
import { mensajeDeError, traducirValidacion } from '../utils/mensajes';

const LINEA = { productoId: '', cantidad: '', unidad: '' };

/**
 * Página de ventas.
 * @returns {JSX.Element} Página.
 */
export default function VentasPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const puedeVender = ['Ventas', 'Administrador'].includes(rol);
  const [datos, setDatos] = useState({ contenido: [], totalElementos: 0 });
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(10);
  const [abierto, setAbierto] = useState(false);

  const cargar = useCallback(async () => {
    try { setDatos(await api.ventas.listar({ pagina, tamano })); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); }
  }, [pagina, tamano, notificar, t]);
  useEffect(() => { cargar(); }, [cargar]);

  const registrar = async (v, { setSubmitting, resetForm }) => {
    try {
      const cuerpo = { clienteId: Number(v.clienteId), lineas: v.lineas.map((l) => ({ productoId: Number(l.productoId), cantidad: Number(l.cantidad), ...(l.unidad.trim() ? { unidad: l.unidad.trim() } : {}) })) };
      const venta = await api.ventas.crear(cuerpo);
      notificar(t('venta.creada', { numero: venta.numeroInterno }));
      resetForm(); setAbierto(false); cargar();
    } catch (e) { notificar(mensajeDeError(t, e), 'error'); }
    finally { setSubmitting(false); }
  };

  const moneda = (n) => new Intl.NumberFormat(undefined, { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(n);

  return (
    <section aria-labelledby="ventas-titulo">
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} mb={2}>
        <Typography id="ventas-titulo" variant="h4" component="h1">{t('venta.titulo')}</Typography>
        {puedeVender && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAbierto(true)}>{t('venta.nueva')}</Button>}
      </Stack>
      <Paper>
        <TableContainer>
          <Table aria-label={t('venta.titulo')} size="small">
            <TableHead><TableRow>
              <TableCell>{t('venta.numero')}</TableCell><TableCell>{t('venta.fecha')}</TableCell>
              <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('venta.factura')}</TableCell><TableCell align="right">{t('venta.total')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {datos.contenido.length === 0 && <TableRow><TableCell colSpan={4} align="center">{t('comun.sinResultados')}</TableCell></TableRow>}
              {datos.contenido.map((v) => (
                <TableRow key={v.id} hover>
                  <TableCell>{v.numeroInterno}</TableCell>
                  <TableCell>{new Date(v.fecha + 'Z').toLocaleString()}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{v.factura?.numeracionDian}</TableCell>
                  <TableCell align="right">{moneda(v.total)}</TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos.totalElementos} page={pagina} rowsPerPage={tamano} rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage={t('comun.filasPorPagina')} onPageChange={(_, p) => setPagina(p)}
          onRowsPerPageChange={(e) => { setTamano(Number(e.target.value)); setPagina(0); }} />
      </Paper>

      <Dialog open={abierto} onClose={() => setAbierto(false)} fullWidth maxWidth="sm" aria-labelledby="venta-form-titulo">
        <DialogTitle id="venta-form-titulo">{t('venta.nueva')}</DialogTitle>
        <Formik initialValues={{ clienteId: '', lineas: [{ ...LINEA }] }} validationSchema={ventaSchema} onSubmit={registrar}>
          {({ values, errors, touched, isSubmitting }) => (
            <Form noValidate>
              <DialogContent>
                <FormTextField name="clienteId" label={t('venta.cliente')} required type="number" inputProps={{ min: 1 }} />
                <Typography variant="subtitle1" component="h2" sx={{ mt: 2 }}>{t('venta.lineas')}</Typography>
                <FieldArray name="lineas">
                  {({ push, remove }) => (
                    <>
                      {values.lineas.map((_, i) => (
                        <Stack key={i} direction={{ xs: 'column', sm: 'row' }} gap={1} alignItems={{ sm: 'flex-start' }} role="group" aria-label={`${t('venta.lineas')} ${i + 1}`}>
                          <FormTextField name={`lineas.${i}.productoId`} label={t('venta.producto')} required type="number" />
                          <FormTextField name={`lineas.${i}.cantidad`} label={t('venta.cantidad')} required type="number" inputProps={{ step: 'any', min: 0 }} />
                          <FormTextField name={`lineas.${i}.unidad`} label={t('venta.unidad')} />
                          <IconButton aria-label={`${t('venta.quitarLinea')} ${i + 1}`} disabled={values.lineas.length === 1} onClick={() => remove(i)} sx={{ mt: 1 }}><DeleteIcon /></IconButton>
                        </Stack>))}
                      {touched.lineas && typeof errors.lineas === 'string' && <Typography color="error" role="alert">{traducirValidacion(t, errors.lineas)}</Typography>}
                      <Button onClick={() => push({ ...LINEA })} startIcon={<AddIcon />} sx={{ mt: 1 }}>{t('venta.agregarLinea')}</Button>
                    </>)}
                </FieldArray>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setAbierto(false)}>{t('comun.cancelar')}</Button>
                <Button type="submit" variant="contained" disabled={isSubmitting}>{t('venta.registrar')}</Button>
              </DialogActions>
            </Form>)}
        </Formik>
      </Dialog>
    </section>
  );
}
