/*
 * Nombre: EntitySelector.jsx
 * Descripción: Selector con búsqueda asíncrona (MUI Autocomplete) reutilizable: retardo de búsqueda, estados de carga, sin resultados y error, y captura de Enter para lectores de código.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useRef, useState } from 'react';
import { Autocomplete, CircularProgress, TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { mensajeDeError } from '../utils/mensajes';

/**
 * @param {object} props
 * @param {(q:string)=>Promise<object[]>} props.buscar función de búsqueda (devuelve opciones)
 * @param {(o:object)=>string} props.etiqueta texto de la opción seleccionada
 * @param {(props:object, o:object)=>JSX.Element} [props.renderOpcion] contenido de cada opción
 * @param {(texto:string)=>Promise<boolean>|boolean} [props.onEnter] Enter con texto (lector de código); true = consumido
 */
export default function EntitySelector({ id, label, valor, onCambio, buscar, etiqueta, renderOpcion, requerido, error,
  textoAyuda, deshabilitado, autoFocus, onEnter, limpiarAlSeleccionar = false, placeholder, minimo = 0, retardo = 300 }) {
  const { t } = useTranslation();
  const [texto, setTexto] = useState('');
  const [opciones, setOpciones] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [falla, setFalla] = useState(null);
  const [abierto, setAbierto] = useState(false);
  const turno = useRef(0);

  useEffect(() => {
    if (!abierto) return undefined;
    if (texto.trim().length < minimo) { setOpciones([]); return undefined; }
    const n = ++turno.current;
    setCargando(true);
    const h = setTimeout(async () => {
      try {
        const r = await buscar(texto.trim());
        if (n === turno.current) { setOpciones(r || []); setFalla(null); }
      } catch (e) {
        if (n === turno.current) { setOpciones([]); setFalla(e); }
      } finally {
        if (n === turno.current) setCargando(false);
      }
    }, retardo);
    return () => clearTimeout(h);
  }, [texto, abierto, buscar, minimo, retardo]);

  return (
    <Autocomplete
      id={id}
      open={abierto}
      onOpen={() => setAbierto(true)}
      onClose={() => setAbierto(false)}
      value={valor ?? null}
      onChange={(_, v) => { onCambio(v); if (limpiarAlSeleccionar) setTexto(''); }}
      inputValue={limpiarAlSeleccionar && !abierto ? texto : undefined}
      onInputChange={(_, v, motivo) => { if (motivo !== 'reset' || !limpiarAlSeleccionar) setTexto(v); }}
      options={opciones}
      filterOptions={(x) => x}
      getOptionLabel={(o) => (o ? etiqueta(o) : '')}
      isOptionEqualToValue={(a, b) => a?.id === b?.id}
      renderOption={renderOpcion ? (props, o) => renderOpcion({ ...props, key: o.id }, o) : undefined}
      loading={cargando}
      loadingText={t('comun.cargando')}
      noOptionsText={falla ? mensajeDeError(t, falla) : texto.trim().length < minimo ? t('comun.escribaParaBuscar') : t('comun.sinResultados')}
      disabled={deshabilitado}
      autoHighlight
      renderInput={(params) => (
        <TextField {...params} label={label} required={requerido} error={Boolean(error)} helperText={error || textoAyuda}
          autoFocus={autoFocus} placeholder={placeholder} margin="dense"
          onKeyDown={async (e) => {
            if (e.key === 'Enter' && onEnter && texto.trim()) {
              const consumido = await onEnter(texto.trim());
              if (consumido) { e.preventDefault(); e.stopPropagation(); setTexto(''); setAbierto(false); }
            }
          }}
          InputProps={{ ...params.InputProps, endAdornment: (<>{cargando ? <CircularProgress color="inherit" size={18} /> : null}{params.InputProps.endAdornment}</>) }} />
      )}
    />
  );
}
