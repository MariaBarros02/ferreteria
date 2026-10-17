/*
 * Nombre: ConteosPage.jsx
 * Descripción: Sesiones de conteo físico (RF-15): listado por estado y apertura de nuevas sesiones.
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
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import { Filtros } from '../../components/Filtros';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { fechaHora } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { SIN_HTML } from '../../validation/schemas';

export default function ConteosPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const navegar = useNavigate();
  const [estado, setEstado] = useState('');
  const [pagina, setPagina] = useState(0);
  const [abrir, setAbrir] = useState(false);
  const [obs, setObs] = useState('');
  const [enviando, setEnviando] = useState(false);
  const { datos, cargando, error, recargar } = useCarga(() => api.conteos.listar({ estado: estado || undefined, pagina, tamano: 20 }), [estado, pagina]);
  const filas = datos?.contenido || [];
  const errorObs = obs && !SIN_HTML.test(obs);

  const crear = async () => {
    if (errorObs) return;
    setEnviando(true);
    try { const s = await api.conteos.abrir({ observaciones: obs.trim() || null }); notificar(t('conteo.abierto', { numero: s.numero })); navegar(`/conteos/${s.id}`); }
    catch (e) { notificar(mensajeDeError(t, e), 'error'); setAbrir(false); }
    finally { setEnviando(false); }
  };

  return (
    <section aria-labelledby="conteos-titulo">
      <PageHeader id="conteos-titulo" titulo={t('conteo.titulo')} descripcion={t('conteo.descripcion')}
        acciones={puede(rol, 'conteos.contar') && <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setObs(''); setAbrir(true); }}>{t('conteo.nuevo')}</Button>} />
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField select size="small" label={t('comun.estado')} value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0); }} sx={{ minWidth: 180 }}>
          <MenuItem value="">{t('comun.todos')}</MenuItem>
          {['ABIERTA', 'CERRADA', 'APROBADA', 'RECHAZADA'].map((s) => <MenuItem key={s} value={s}>{t(`estados.${s}`)}</MenuItem>)}
        </TextField>
      </Filtros>
      <Paper>
        <TableContainer>
          <Table size="small" aria-label={t('conteo.titulo')}>
            <TableHead><TableRow>
              <TableCell>{t('conteo.numero')}</TableCell><TableCell>{t('conteo.inicio')}</TableCell><TableCell>{t('conteo.responsable')}</TableCell>
              <TableCell align="right">{t('conteo.contados')}</TableCell><TableCell align="right">{t('conteo.conDiferencia')}</TableCell><TableCell>{t('comun.estado')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={6} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((s) => (
                <TableRow key={s.id} hover sx={{ cursor: 'pointer' }} onClick={() => navegar(`/conteos/${s.id}`)}>
                  <TableCell><RouterLink to={`/conteos/${s.id}`} onClick={(e) => e.stopPropagation()}>{s.numero}</RouterLink></TableCell>
                  <TableCell>{fechaHora(s.fechaInicio)}</TableCell><TableCell>{s.usuario}</TableCell>
                  <TableCell align="right">{s.productosContados}</TableCell><TableCell align="right">{s.productosConDiferencia}</TableCell>
                  <TableCell><EstadoChip estado={s.estado} /></TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
          onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
      </Paper>
      <ConfirmDialog abierto={abrir} titulo={t('conteo.nuevo')} mensaje={t('conteo.nuevoMensaje')} color="primary" textoConfirmar={t('conteo.abrir')}
        cargando={enviando} onConfirmar={crear} onCancelar={() => setAbrir(false)}>
        <TextField label={t('comun.observaciones')} value={obs} onChange={(e) => setObs(e.target.value.slice(0, 500))} fullWidth multiline margin="dense"
          error={Boolean(errorObs)} helperText={errorObs ? t('errores.sinHtml') : null} />
      </ConfirmDialog>
    </section>
  );
}
