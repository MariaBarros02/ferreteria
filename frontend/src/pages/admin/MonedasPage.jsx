/*
 * Nombre: MonedasPage.jsx
 * Descripción: Monedas y tasas de cambio (RF-23, RN-17): moneda funcional COP, monedas activas con tasa vigente, registro de tasas con fecha de vigencia y fuente (Gerencia/Administrador), historial inmutable, alta y activación de monedas, y conversor entre monedas.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid,
  MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField,
  Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/http';
import { useAuth } from '../../auth/AuthContext';
import ConfirmDialog from '../../components/ConfirmDialog';
import EstadoChip from '../../components/EstadoChip';
import { useNotify } from '../../components/Notifier';
import PageHeader from '../../components/PageHeader';
import TablaEstado from '../../components/TablaEstado';
import useCarga from '../../hooks/useCarga';
import { fecha, fechaHora, hoyIso, moneda, tasaTexto } from '../../utils/formato';
import { mensajeDeError } from '../../utils/mensajes';
import { puede } from '../../utils/permisos';
import { numero } from '../../validation/lineas';
import { SIN_HTML } from '../../validation/schemas';

/** Tasa: positiva, hasta 12 enteros y 6 decimales (acepta coma decimal). */
export const errorTasa = (v) => {
  const s = String(v ?? '').trim().replace(',', '.');
  if (!s) return 'errores.obligatorio';
  if (!/^\d{1,12}(\.\d{1,6})?$/.test(s) || Number(s) <= 0) return 'moneda.tasaInvalida';
  return null;
};

/** Código ISO 4217: tres letras. */
export const errorCodigo = (v) => (/^[A-Za-z]{3}$/.test(String(v ?? '').trim()) ? null : 'moneda.codigoInvalido');

function TasaDialog({ moneda: m, onCerrar, onHecha }) {
  const { t } = useTranslation();
  const [v, setV] = useState({ tasa: '', fechaVigencia: hoyIso(), fuente: '' });
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  useEffect(() => { if (m) { setV({ tasa: '', fechaVigencia: hoyIso(), fuente: '' }); setIntentado(false); setFalla(null); } }, [m]);
  const errores = {
    tasa: errorTasa(v.tasa) && t(errorTasa(v.tasa)),
    fechaVigencia: !v.fechaVigencia ? t('errores.obligatorio') : v.fechaVigencia > hoyIso() ? t('moneda.fechaFutura') : null,
    fuente: v.fuente && !SIN_HTML.test(v.fuente) ? t('errores.sinHtml') : null,
  };
  const e = (k) => (intentado ? errores[k] : null);
  const enviar = async () => {
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setEnviando(true); setFalla(null);
    try {
      await api.monedas.registrarTasa(m.codigo, { tasa: numero(v.tasa), fechaVigencia: v.fechaVigencia, fuente: v.fuente.trim() || null });
      onHecha();
    } catch (x) { setFalla(mensajeDeError(t, x)); }
    finally { setEnviando(false); }
  };
  return (
    <Dialog open={Boolean(m)} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="sm" aria-labelledby="tasa-titulo">
      <DialogTitle id="tasa-titulo">{t('moneda.registrarTasaDe', { codigo: m?.codigo })}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <Typography variant="body2" color="text.secondary" gutterBottom>{t('moneda.tasaAyuda', { codigo: m?.codigo })}</Typography>
        {m?.tasaVigente && <Typography variant="body2" gutterBottom>{t('moneda.tasaActual')}: {tasaTexto(m.codigo, m.tasaVigente)} ({fecha(m.fechaTasa)})</Typography>}
        <TextField label={t('moneda.tasaCop', { codigo: m?.codigo })} value={v.tasa} onChange={(x) => setV({ ...v, tasa: x.target.value })} required fullWidth margin="dense" autoFocus
          inputProps={{ inputMode: 'decimal' }} error={Boolean(e('tasa'))} helperText={e('tasa') || (numero(v.tasa) > 0 ? tasaTexto(m?.codigo, numero(v.tasa)) : null)} />
        <TextField type="date" label={t('moneda.fechaVigencia')} value={v.fechaVigencia} onChange={(x) => setV({ ...v, fechaVigencia: x.target.value })} required fullWidth margin="dense"
          InputLabelProps={{ shrink: true }} inputProps={{ max: hoyIso() }} error={Boolean(e('fechaVigencia'))} helperText={e('fechaVigencia')} />
        <TextField label={t('moneda.fuente')} value={v.fuente} onChange={(x) => setV({ ...v, fuente: x.target.value.slice(0, 100) })} fullWidth margin="dense"
          placeholder={t('moneda.fuenteEjemplo')} error={Boolean(e('fuente'))} helperText={e('fuente')} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando} startIcon={enviando ? <CircularProgress size={16} color="inherit" /> : null}>{t('moneda.registrarTasa')}</Button>
      </DialogActions>
    </Dialog>
  );
}

function HistorialDialog({ moneda: m, onCerrar }) {
  const { t } = useTranslation();
  const [pagina, setPagina] = useState(0);
  useEffect(() => { setPagina(0); }, [m]);
  const { datos, cargando, error, recargar } = useCarga(() => (m ? api.monedas.tasas(m.codigo, { pagina, tamano: 20 }) : Promise.resolve(null)), [m?.codigo, pagina]);
  const filas = datos?.contenido || [];
  return (
    <Dialog open={Boolean(m)} onClose={onCerrar} fullWidth maxWidth="md" aria-labelledby="historial-tasas-titulo">
      <DialogTitle id="historial-tasas-titulo">{t('moneda.historialDe', { codigo: m?.codigo })}</DialogTitle>
      <DialogContent>
        <TableContainer>
          <Table size="small" aria-label={t('moneda.historialDe', { codigo: m?.codigo })}>
            <TableHead><TableRow>
              <TableCell>{t('moneda.fechaVigencia')}</TableCell><TableCell align="right">{t('moneda.tasa')}</TableCell>
              <TableCell>{t('moneda.fuente')}</TableCell><TableCell>{t('moneda.registradaPor')}</TableCell>
            </TableRow></TableHead>
            <TableBody>
              <TablaEstado columnas={4} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} textoVacio={t('moneda.sinTasas')} />
              {!error && filas.map((x) => (
                <TableRow key={x.id}>
                  <TableCell>{fecha(x.fechaVigencia)}</TableCell>
                  <TableCell align="right">{tasaTexto(x.codigoMoneda, x.tasa)}</TableCell>
                  <TableCell>{x.fuente || '—'}</TableCell>
                  <TableCell>{x.usuario || '—'}<Typography variant="caption" component="div" color="text.secondary">{fechaHora(x.fechaRegistro)}</Typography></TableCell>
                </TableRow>))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={datos?.totalElementos || 0} page={pagina} rowsPerPage={20} rowsPerPageOptions={[20]}
          onPageChange={(_, p) => setPagina(p)} labelRowsPerPage={t('comun.filasPorPagina')} />
        <Typography variant="caption" color="text.secondary">{t('moneda.inmutable')}</Typography>
      </DialogContent>
      <DialogActions><Button onClick={onCerrar}>{t('comun.cerrar')}</Button></DialogActions>
    </Dialog>
  );
}

function NuevaMonedaDialog({ abierto, onCerrar, onHecha }) {
  const { t } = useTranslation();
  const [v, setV] = useState({ codigo: '', nombre: '', simbolo: '', decimales: '2' });
  const [intentado, setIntentado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [falla, setFalla] = useState(null);
  useEffect(() => { if (abierto) { setV({ codigo: '', nombre: '', simbolo: '', decimales: '2' }); setIntentado(false); setFalla(null); } }, [abierto]);
  const errores = {
    codigo: errorCodigo(v.codigo) && t(errorCodigo(v.codigo)),
    nombre: !v.nombre.trim() ? t('errores.obligatorio') : !SIN_HTML.test(v.nombre) ? t('errores.sinHtml') : null,
    simbolo: !v.simbolo.trim() ? t('errores.obligatorio') : !SIN_HTML.test(v.simbolo) ? t('errores.sinHtml') : null,
  };
  const e = (k) => (intentado ? errores[k] : null);
  const enviar = async () => {
    setIntentado(true);
    if (Object.values(errores).some(Boolean)) return;
    setEnviando(true); setFalla(null);
    try {
      const m = await api.monedas.crear({ codigo: v.codigo.trim().toUpperCase(), nombre: v.nombre.trim(), simbolo: v.simbolo.trim(), decimales: Number(v.decimales) });
      onHecha(m);
    } catch (x) { setFalla(mensajeDeError(t, x)); }
    finally { setEnviando(false); }
  };
  return (
    <Dialog open={abierto} onClose={enviando ? undefined : onCerrar} fullWidth maxWidth="xs" aria-labelledby="nueva-moneda-titulo">
      <DialogTitle id="nueva-moneda-titulo">{t('moneda.nueva')}</DialogTitle>
      <DialogContent>
        {falla && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{falla}</Alert>}
        <TextField label={t('moneda.codigo')} value={v.codigo} onChange={(x) => setV({ ...v, codigo: x.target.value.slice(0, 3).toUpperCase() })} required fullWidth margin="dense" autoFocus
          error={Boolean(e('codigo'))} helperText={e('codigo') || t('moneda.codigoAyuda')} />
        <TextField label={t('moneda.nombre')} value={v.nombre} onChange={(x) => setV({ ...v, nombre: x.target.value.slice(0, 60) })} required fullWidth margin="dense"
          error={Boolean(e('nombre'))} helperText={e('nombre')} />
        <Stack direction="row" gap={2}>
          <TextField label={t('moneda.simbolo')} value={v.simbolo} onChange={(x) => setV({ ...v, simbolo: x.target.value.slice(0, 5) })} required fullWidth margin="dense"
            error={Boolean(e('simbolo'))} helperText={e('simbolo')} />
          <TextField select label={t('moneda.decimales')} value={v.decimales} onChange={(x) => setV({ ...v, decimales: x.target.value })} fullWidth margin="dense">
            {['0', '1', '2', '3', '4'].map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>{t('comun.cancelar')}</Button>
        <Button variant="contained" onClick={enviar} disabled={enviando}>{t('comun.guardar')}</Button>
      </DialogActions>
    </Dialog>
  );
}

function Conversor({ monedas }) {
  const { t } = useTranslation();
  const [v, setV] = useState({ monto: '100', desde: 'USD', hacia: 'COP' });
  const [r, setR] = useState(null);
  const [falla, setFalla] = useState(null);
  const [cargando, setCargando] = useState(false);
  const disponibles = monedas.filter((m) => m.activa && (m.funcional || m.tasaVigente));
  const convertir = async () => {
    const s = String(v.monto).trim().replace(',', '.');
    if (!/^\d+(\.\d{1,4})?$/.test(s)) { setFalla(t('moneda.montoInvalido')); return; }
    setCargando(true); setFalla(null);
    try { setR(await api.monedas.convertir(Number(s), v.desde, v.hacia)); }
    catch (x) { setFalla(mensajeDeError(t, x)); setR(null); }
    finally { setCargando(false); }
  };
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="h6" component="h2" gutterBottom>{t('moneda.conversor')}</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ sm: 'flex-start' }}>
          <TextField size="small" label={t('moneda.monto')} value={v.monto} onChange={(x) => setV({ ...v, monto: x.target.value })} inputProps={{ inputMode: 'decimal' }} />
          {['desde', 'hacia'].map((k) => (
            <TextField key={k} select size="small" label={t(`moneda.${k}`)} value={v[k]} onChange={(x) => setV({ ...v, [k]: x.target.value })} sx={{ minWidth: 120 }}>
              {disponibles.map((m) => <MenuItem key={m.codigo} value={m.codigo}>{m.codigo}</MenuItem>)}
            </TextField>))}
          <Button variant="outlined" onClick={convertir} disabled={cargando}>{t('moneda.convertir')}</Button>
        </Stack>
        {falla && <Alert severity="warning" sx={{ mt: 2 }}>{falla}</Alert>}
        {r && (
          <Box sx={{ mt: 2 }} role="status">
            <Typography variant="h5" component="p" data-testid="resultado-conversion">{moneda(r.monto, undefined, r.desde)} = {moneda(r.resultado, undefined, r.hacia)}</Typography>
            <Typography variant="caption" color="text.secondary">
              {[r.desde, r.hacia].filter((c) => c !== 'COP').map((c) => tasaTexto(c, c === r.desde ? r.tasaDesde : r.tasaHacia)).join(' · ')} · {fecha(r.fecha)}
            </Typography>
          </Box>)}
      </CardContent>
    </Card>
  );
}

export default function MonedasPage() {
  const { t } = useTranslation();
  const { rol } = useAuth();
  const notificar = useNotify();
  const gestionar = puede(rol, 'monedas.gestionar');
  const { datos, cargando, error, recargar } = useCarga(() => api.monedas.listar(false), [], null);
  const [tasa, setTasa] = useState(null);
  const [historial, setHistorial] = useState(null);
  const [nueva, setNueva] = useState(false);
  const [estado, setEstado] = useState(null);
  const filas = datos || [];
  const cambiarEstado = async () => {
    try { await api.monedas.cambiarEstado(estado.codigo, !estado.activa); notificar(t(estado.activa ? 'moneda.desactivada' : 'moneda.activada')); setEstado(null); recargar(); }
    catch (x) { notificar(mensajeDeError(t, x), 'error'); setEstado(null); }
  };
  return (
    <section aria-labelledby="monedas-titulo">
      <PageHeader id="monedas-titulo" titulo={t('moneda.titulo')} descripcion={t('moneda.descripcion')}
        acciones={gestionar && <Button variant="outlined" startIcon={<AddIcon />} onClick={() => setNueva(true)}>{t('moneda.nueva')}</Button>} />
      <Grid container spacing={2}>
        <Grid item xs={12} lg={8}>
          <Paper>
            <TableContainer>
              <Table size="small" aria-label={t('moneda.titulo')}>
                <TableHead><TableRow>
                  <TableCell>{t('moneda.moneda')}</TableCell>
                  <TableCell>{t('moneda.tasaVigente')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('moneda.fuente')}</TableCell>
                  <TableCell>{t('comun.estado')}</TableCell>
                  <TableCell align="right">{t('comun.acciones')}</TableCell>
                </TableRow></TableHead>
                <TableBody>
                  <TablaEstado columnas={5} cargando={cargando && !datos} error={error} vacio={!filas.length} onReintentar={recargar} />
                  {!error && filas.map((m) => (
                    <TableRow key={m.codigo}>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{m.codigo} · {m.nombre}</Typography>
                        <Typography variant="caption" color="text.secondary">{m.simbolo} · {t('moneda.decimalesN', { n: m.decimales })}</Typography>
                        {m.funcional && <Chip size="small" color="primary" sx={{ ml: 1 }} label={t('moneda.funcional')} />}
                      </TableCell>
                      <TableCell>
                        {m.funcional ? '—' : m.tasaVigente ? <>{tasaTexto(m.codigo, m.tasaVigente)}<Typography variant="caption" component="div" color="text.secondary">{t('moneda.desde')} {fecha(m.fechaTasa)}</Typography></>
                          : <Typography variant="body2" color="warning.main">{t('moneda.sinTasa')}</Typography>}
                      </TableCell>
                      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{m.fuenteTasa || '—'}</TableCell>
                      <TableCell><EstadoChip estado={m.activa ? 'ACTIVO' : 'INACTIVO'} /></TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {!m.funcional && <Button size="small" onClick={() => setHistorial(m)}>{t('moneda.historial')}</Button>}
                        {!m.funcional && gestionar && <Button size="small" onClick={() => setTasa(m)}>{t('moneda.registrarTasa')}</Button>}
                        {!m.funcional && gestionar && <Button size="small" color={m.activa ? 'warning' : 'primary'} onClick={() => setEstado(m)}>{m.activa ? t('producto.desactivar') : t('producto.activar')}</Button>}
                      </TableCell>
                    </TableRow>))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{t('moneda.politica')}</Typography>
        </Grid>
        <Grid item xs={12} lg={4}><Conversor monedas={filas} /></Grid>
      </Grid>
      <TasaDialog moneda={tasa} onCerrar={() => setTasa(null)} onHecha={() => { setTasa(null); notificar(t('moneda.tasaRegistrada')); recargar(); }} />
      <HistorialDialog moneda={historial} onCerrar={() => setHistorial(null)} />
      <NuevaMonedaDialog abierto={nueva} onCerrar={() => setNueva(false)} onHecha={(m) => { setNueva(false); notificar(t('moneda.creada', { codigo: m.codigo })); recargar(); }} />
      <ConfirmDialog abierto={Boolean(estado)} titulo={estado?.activa ? t('producto.desactivar') : t('producto.activar')} color={estado?.activa ? 'warning' : 'primary'}
        mensaje={estado?.activa ? t('moneda.desactivarMensaje', { codigo: estado?.codigo }) : t('moneda.activarMensaje', { codigo: estado?.codigo })}
        textoConfirmar={estado?.activa ? t('producto.desactivar') : t('producto.activar')} onConfirmar={cambiarEstado} onCancelar={() => setEstado(null)} />
    </section>
  );
}
