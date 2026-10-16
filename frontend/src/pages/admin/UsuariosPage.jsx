/*
 * Nombre: UsuariosPage.jsx
 * Descripción: Administración de usuarios y roles (RF-09): listado con búsqueda, creación y edición (rol, estado), cambio de contraseña con política de complejidad y consulta de permisos por rol. Solo Administrador.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useState } from 'react';
import { Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, Paper, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Tooltip, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import KeyIcon from '@mui/icons-material/Key';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import EstadoChip from '../../components/EstadoChip';
import { Filtros } from '../../components/Filtros';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { mensajeDeError } from '../../utils/mensajes';
import { SIN_HTML } from '../../validation/schemas';

/** Política de contraseñas (misma regla que el servidor): 10–128 caracteres con minúscula, mayúscula y dígito. */
export const CLAVE_SEGURA = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{10,128}$/;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function UsuarioDialog({ usuario, roles, onCerrar, onGuardado }) {
  const { t } = useTranslation();
  const nuevo = usuario && !usuario.id;
  const [v, setV] = useState({ nombre: '', correo: '', rol: '', estado: 'ACTIVO', password: '' });
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  useEffect(() => {
    if (usuario) { setV({ nombre: usuario.nombre || '', correo: usuario.correo || '', rol: usuario.rol || '', estado: usuario.estado || 'ACTIVO', password: '' }); setIntentado(false); setFalla(null); }
  }, [usuario]);
  const errores = {
    nombre: !v.nombre.trim() ? t('errores.obligatorio') : !SIN_HTML.test(v.nombre) ? t('errores.sinHtml') : null,
    correo: !CORREO.test(v.correo.trim()) ? t('errores.correo') : null,
    rol: !v.rol ? t('errores.obligatorio') : null,
    password: nuevo && !CLAVE_SEGURA.test(v.password) ? t('usuario.politicaClave') : null,
  };
  const e = (k) => (intentado ? errores[k] : null);
  const guardar = async () => {
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setEnviando(true); setFalla(null);
    try {
      const cuerpo = { nombre: v.nombre.trim(), correo: v.correo.trim().toLowerCase(), rol: v.rol, estado: v.estado, ...(nuevo ? { password: v.password } : {}) };
      if (nuevo) await api.usuarios.crear(cuerpo); else await api.usuarios.actualizar(usuario.id, cuerpo);
      onGuardado();
    } catch (x) { setFalla(mensajeDeError(t, x)); }
    finally { setEnviando(false); }
  };
  return (
    <Dialog open={Boolean(usuario)} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="sm" aria-labelledby="usuario-titulo">
      <DialogTitle id="usuario-titulo">{nuevo ? t('usuario.nuevo') : t('usuario.editar')}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <TextField label={t('usuario.nombre')} value={v.nombre} onChange={(x) => setV({ ...v, nombre: x.target.value.slice(0, 150) })} required fullWidth margin="dense" autoFocus
          error={Boolean(e('nombre'))} helperText={e('nombre')} />
        <TextField label={t('tercero.correo')} type="email" value={v.correo} onChange={(x) => setV({ ...v, correo: x.target.value.slice(0, 150) })} required fullWidth margin="dense"
          error={Boolean(e('correo'))} helperText={e('correo')} autoComplete="off" />
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2}>
          <TextField select label={t('usuario.rol')} value={v.rol} onChange={(x) => setV({ ...v, rol: x.target.value })} required fullWidth margin="dense"
            error={Boolean(e('rol'))} helperText={e('rol')}>
            {roles.map((r) => <MenuItem key={r.id} value={r.nombre}>{r.nombre}</MenuItem>)}
          </TextField>
          <TextField select label={t('comun.estado')} value={v.estado} onChange={(x) => setV({ ...v, estado: x.target.value })} fullWidth margin="dense">
            <MenuItem value="ACTIVO">{t('estados.ACTIVO')}</MenuItem>
            <MenuItem value="INACTIVO">{t('estados.INACTIVO')}</MenuItem>
          </TextField>
        </Stack>
        {nuevo && (
          <TextField label={t('login.password')} type="password" value={v.password} onChange={(x) => setV({ ...v, password: x.target.value.slice(0, 128) })} required fullWidth margin="dense"
            autoComplete="new-password" error={Boolean(e('password'))} helperText={e('password') || t('usuario.politicaClave')} />)}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={guardar} disabled={enviando}>{t('comun.guardar')}</Button>
      </DialogActions>
    </Dialog>
  );
}

function ClaveDialog({ usuario, onCerrar, onHecho }) {
  const { t } = useTranslation();
  const [clave, setClave] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  useEffect(() => { setClave(''); setConfirmacion(''); setIntentado(false); setFalla(null); }, [usuario]);
  const error = !CLAVE_SEGURA.test(clave) ? t('usuario.politicaClave') : null;
  const errorConf = clave !== confirmacion ? t('usuario.noCoinciden') : null;
  const enviar = async () => {
    setIntentado(true);
    if (error || errorConf) return;
    setEnviando(true); setFalla(null);
    try { await api.usuarios.clave(usuario.id, clave); onHecho(); }
    catch (x) { setFalla(mensajeDeError(t, x)); }
    finally { setEnviando(false); }
  };
  return (
    <Dialog open={Boolean(usuario)} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="xs" aria-labelledby="clave-titulo">
      <DialogTitle id="clave-titulo">{t('usuario.cambiarClave', { correo: usuario?.correo })}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <TextField label={t('usuario.nuevaClave')} type="password" value={clave} onChange={(x) => setClave(x.target.value.slice(0, 128))} fullWidth margin="dense" required autoFocus
          autoComplete="new-password" error={intentado && Boolean(error)} helperText={t('usuario.politicaClave')} />
        <TextField label={t('usuario.confirmarClave')} type="password" value={confirmacion} onChange={(x) => setConfirmacion(x.target.value.slice(0, 128))} fullWidth margin="dense" required
          autoComplete="new-password" error={intentado && Boolean(errorConf)} helperText={intentado ? errorConf : null} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando}>{t('comun.guardar')}</Button>
      </DialogActions>
    </Dialog>
  );
}

export default function UsuariosPage() {
  const { t } = useTranslation();
  const { usuario: actual } = useAuth();
  const notificar = useNotify();
  const [q, setQ] = useState('');
  const [pagina, setPagina] = useState(0);
  const [editando, setEditando] = useState(null);
  const [clave, setClave] = useState(null);
  const roles = useCarga(() => api.usuarios.roles(), [], []);
  const { datos, cargando, error, recargar } = useCarga(() => api.usuarios.listar({ q: q || undefined, pagina, tamano: 20 }), [q, pagina]);
  const filas = datos?.contenido || [];
  return (
    <section aria-labelledby="usuarios-titulo">
      <PageHeader id="usuarios-titulo" titulo={t('usuario.titulo')} descripcion={t('usuario.descripcion')}
        acciones={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditando({})}>{t('usuario.nuevo')}</Button>} />
      <Filtros etiqueta={t('comun.filtros')}>
        <TextField size="small" type="search" label={t('comun.buscar')} value={q} onChange={(e) => { setQ(e.target.value.slice(0, 100)); setPagina(0); }} sx={{ flexGrow: 1 }} />
      </Filtros>
      <Paper sx={{ mb: 2 }}>
        <TableContainer>
          <Table size="small" aria-label={t('usuario.titulo')}>
            <TableHead><TableRow>
              <TableCell>{t('usuario.nombre')}</TableCell><TableCell>{t('tercero.correo')}</TableCell><TableCell>{t('usuario.rol')}</TableCell>
              <TableCell>{t('comun.estado')}</TableCell><TableCell align="right">{t('comun.acciones')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={5} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
              {!error && filas.map((u) => (
                <TableRow key={u.id}>
                  <TableCell>{u.nombre}{actual?.correo === u.correo && <Chip size="small" sx={{ ml: 1 }} label={t('usuario.tu')} />}</TableCell>
                  <TableCell>{u.correo}</TableCell><TableCell>{u.rol}</TableCell><TableCell><EstadoChip estado={u.estado} /></TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title={t('comun.editar')}><IconButton aria-label={`${t('comun.editar')} ${u.correo}`} onClick={() => setEditando(u)}><EditIcon /></IconButton></Tooltip>
                    <Tooltip title={t('usuario.cambiarClaveCorto')}><IconButton aria-label={`${t('usuario.cambiarClaveCorto')} ${u.correo}`} onClick={() => setClave(u)}><KeyIcon /></IconButton></Tooltip>
                  </TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
          onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
      </Paper>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>{t('usuario.permisosPorRol')}</Typography>
        <Stack gap={1.5}>
          {(roles.datos || []).map((r) => (
            <Stack key={r.id} direction={{ xs: 'column', sm: 'row' }} gap={1} alignItems={{ sm: 'center' }}>
              <Typography sx={{ fontWeight: 600, minWidth: 140 }}>{r.nombre}</Typography>
              <Stack direction="row" gap={0.5} flexWrap="wrap">
                {Object.entries(r.permisos || {}).map(([modulo, nivel]) => <Chip key={modulo} size="small" variant="outlined" label={`${modulo}: ${nivel}`} />)}
              </Stack>
            </Stack>))}
        </Stack>
      </Paper>
      <UsuarioDialog usuario={editando} roles={roles.datos || []} onCerrar={() => setEditando(null)} onGuardado={() => { setEditando(null); notificar(t('comun.guardado')); recargar(); }} />
      <ClaveDialog usuario={clave} onCerrar={() => setClave(null)} onHecho={() => { setClave(null); notificar(t('usuario.claveCambiada')); }} />
    </section>
  );
}
