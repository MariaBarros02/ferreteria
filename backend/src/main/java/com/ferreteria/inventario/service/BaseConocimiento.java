/*
 * Nombre: BaseConocimiento.java
 * Descripción: Modelo de la base de conocimiento del asistente virtual: árbol de decisión con nodos (título, respuesta y palabras clave por idioma, opciones hijas, roles y enlace a una pantalla). Se carga de resources/asistente/base-conocimiento.json.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import java.util.List;
import java.util.Map;

/**
 * Base de conocimiento del asistente.
 *
 * @param version versión del contenido
 * @param raiz ids de los temas del menú principal
 * @param nodos nodos del árbol
 */
public record BaseConocimiento(String version, List<String> raiz, List<Nodo> nodos) {

    /** Idioma por defecto y de respaldo de los textos. */
    public static final String IDIOMA_DEFECTO = "es";

    /** Normaliza listas nulas. */
    public BaseConocimiento {
        raiz = raiz == null ? List.of() : List.copyOf(raiz);
        nodos = nodos == null ? List.of() : List.copyOf(nodos);
    }

    /**
     * Nodo del árbol de decisión.
     *
     * @param id identificador estable (minúsculas, números y guiones)
     * @param oculto no aparece en menús (saludo, agradecimiento)
     * @param titulo título por idioma (texto de la opción)
     * @param respuesta respuesta por idioma
     * @param claves palabras o frases clave por idioma
     * @param opciones ids de los nodos hijos
     * @param roles roles que pueden realizar la operación descrita (vacío = todos)
     * @param enlace pantalla relacionada (opcional)
     * @param opcionesRaiz responde con los temas del menú principal como opciones
     */
    public record Nodo(String id, boolean oculto, Map<String, String> titulo, Map<String, String> respuesta,
                       Map<String, List<String>> claves, List<String> opciones, List<String> roles, Enlace enlace,
                       boolean opcionesRaiz) {

        /** Normaliza colecciones nulas. */
        public Nodo {
            titulo = titulo == null ? Map.of() : Map.copyOf(titulo);
            respuesta = respuesta == null ? Map.of() : Map.copyOf(respuesta);
            claves = claves == null ? Map.of() : Map.copyOf(claves);
            opciones = opciones == null ? List.of() : List.copyOf(opciones);
            roles = roles == null ? List.of() : List.copyOf(roles);
        }

        /** @param idioma es/en @return título en ese idioma (o en español) */
        public String tituloEn(String idioma) {
            return texto(titulo, idioma);
        }

        /** @param idioma es/en @return respuesta en ese idioma (o en español) */
        public String respuestaEn(String idioma) {
            return texto(respuesta, idioma);
        }
    }

    /**
     * Enlace a una pantalla de la aplicación.
     *
     * @param ruta ruta del frontend (p. ej. /ventas/nueva)
     * @param roles roles que pueden abrir la pantalla; vacío = los del nodo
     * @param texto texto del botón por idioma
     */
    public record Enlace(String ruta, List<String> roles, Map<String, String> texto) {

        /** Normaliza colecciones nulas. */
        public Enlace {
            roles = roles == null ? List.of() : List.copyOf(roles);
            texto = texto == null ? Map.of() : Map.copyOf(texto);
        }

        /** @param idioma es/en @return texto del botón en ese idioma (o en español) */
        public String textoEn(String idioma) {
            return BaseConocimiento.texto(texto, idioma);
        }
    }

    static String texto(Map<String, String> porIdioma, String idioma) {
        String t = porIdioma.get(idioma);
        return t != null ? t : porIdioma.getOrDefault(IDIOMA_DEFECTO, "");
    }
}
