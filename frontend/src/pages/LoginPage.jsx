/*
 * Nombre: LoginPage.jsx
 * Descripción: Página de inicio de sesión: Formik + Yup, mostrar/ocultar contraseña, "mantener sesión" y errores accesibles.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Box, Button, Checkbox, FormControlLabel, IconButton, InputAdornment, Paper, TextField, Typography } from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { Form, Formik } from 'formik';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { loginSchema } from '../validation/schemas';
import { mensajeDeError, traducirValidacion } from '../utils/mensajes';

/**
 * Pantalla de login.
 * @returns {JSX.Element} Página.
 */
export default function LoginPage() {
  const { t } = useTranslation();
  const { login, autenticado, sesionExpirada } = useAuth();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const [verPassword, setVerPassword] = useState(false);
  const [error, setError] = useState(null);

  if (autenticado) return <Navigate to={ubicacion.state?.desde?.pathname || '/'} replace />;

  return (
    <Box component="main" sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2 }}>
      <Paper sx={{ p: { xs: 3, sm: 4 }, width: '100%', maxWidth: 420 }} component="section" aria-labelledby="login-titulo">
        <Typography id="login-titulo" variant="h5" component="h1" gutterBottom>{t('login.titulo')}</Typography>
        {sesionExpirada && <Alert severity="info" sx={{ mb: 2 }}>{t('errores.sesionExpirada')}</Alert>}
        {error && <Alert severity="error" role="alert" sx={{ mb: 2 }}>{error}</Alert>}
        <Formik
          initialValues={{ correo: '', password: '', recordar: false }}
          validationSchema={loginSchema}
          onSubmit={async (v, { setSubmitting }) => {
            setError(null);
            try {
              await login(v.correo.trim(), v.password, v.recordar);
              navegar(ubicacion.state?.desde?.pathname || '/', { replace: true });
            } catch (e) {
              setError(mensajeDeError(t, e));
            } finally {
              setSubmitting(false);
            }
          }}
        >
          {({ values, errors, touched, handleChange, handleBlur, isSubmitting }) => (
            <Form noValidate aria-label={t('login.titulo')}>
              <TextField id="correo" name="correo" type="email" label={t('login.correo')} fullWidth margin="normal" required
                autoComplete="username" value={values.correo} onChange={handleChange} onBlur={handleBlur}
                error={Boolean(touched.correo && errors.correo)} helperText={touched.correo ? traducirValidacion(t, errors.correo) : ' '} />
              <TextField id="password" name="password" type={verPassword ? 'text' : 'password'} label={t('login.password')} fullWidth margin="normal" required
                autoComplete="current-password" value={values.password} onChange={handleChange} onBlur={handleBlur}
                error={Boolean(touched.password && errors.password)} helperText={touched.password ? traducirValidacion(t, errors.password) : ' '}
                InputProps={{ endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={() => setVerPassword((v) => !v)} aria-label={verPassword ? t('login.ocultar') : t('login.mostrar')} edge="end">
                      {verPassword ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  </InputAdornment>) }} />
              <FormControlLabel control={<Checkbox name="recordar" checked={values.recordar} onChange={handleChange} />} label={t('login.recordarme')} />
              <Button type="submit" variant="contained" fullWidth size="large" disabled={isSubmitting} sx={{ mt: 1 }}>{t('login.entrar')}</Button>
            </Form>
          )}
        </Formik>
      </Paper>
    </Box>
  );
}
