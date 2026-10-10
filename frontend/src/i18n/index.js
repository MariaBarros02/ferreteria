/*
 * Nombre: index.js
 * Descripción: Configuración de internacionalización (react-i18next): español por defecto, inglés; idioma persistente.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import es from './locales/es.json';
import en from './locales/en.json';

const CLAVE = 'idioma';

/**
 * Lee el idioma guardado de forma segura.
 * @returns {'es'|'en'} Idioma inicial.
 */
export function idiomaInicial() {
  try {
    const g = localStorage.getItem(CLAVE);
    return g === 'en' ? 'en' : 'es';
  } catch {
    return 'es';
  }
}

/**
 * Cambia y persiste el idioma.
 * @param {'es'|'en'} lng - Idioma.
 */
export function cambiarIdioma(lng) {
  i18n.changeLanguage(lng);
  try { localStorage.setItem(CLAVE, lng); } catch { /* almacenamiento no disponible */ }
  document.documentElement.lang = lng;
}

i18n.use(initReactI18next).init({
  resources: { es: { translation: es }, en: { translation: en } },
  lng: idiomaInicial(),
  fallbackLng: 'es',
  interpolation: { escapeValue: false }, // React ya escapa; no se usa dangerouslySetInnerHTML
});
document.documentElement.lang = i18n.language;

export default i18n;
