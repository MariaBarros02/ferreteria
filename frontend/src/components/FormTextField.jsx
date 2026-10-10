/*
 * Nombre: FormTextField.jsx
 * Descripción: Campo de formulario accesible enlazado a Formik: etiqueta, error traducido, aria-invalid y autocompletado controlado.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { MenuItem, TextField } from '@mui/material';
import { useField } from 'formik';
import { useTranslation } from 'react-i18next';
import { traducirValidacion } from '../utils/mensajes';

/**
 * Campo de texto/selección conectado a Formik.
 * @param {object} props - Propiedades.
 * @param {string} props.name - Nombre del campo en Formik.
 * @param {string} props.label - Etiqueta visible (siempre presente: accesibilidad).
 * @param {Array<{value:string,label:string}>} [props.options] - Si existe, renderiza un selector.
 * @returns {JSX.Element} Campo.
 */
export default function FormTextField({ name, label, options, required, ...resto }) {
  const { t } = useTranslation();
  const [campo, meta] = useField(name);
  const mostrarError = Boolean(meta.touched && meta.error);
  return (
    <TextField
      {...campo}
      {...resto}
      id={`campo-${name}`}
      label={label}
      required={required}
      select={Boolean(options)}
      fullWidth
      margin="dense"
      error={mostrarError}
      helperText={mostrarError ? traducirValidacion(t, meta.error) : resto.helperText}
      value={campo.value ?? ''}
      inputProps={{ maxLength: resto.maxLength, ...(resto.inputProps || {}) }}
    >
      {options?.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
    </TextField>
  );
}
