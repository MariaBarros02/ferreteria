/*
 * Nombre: AsistenteRespuesta.java
 * Descripción: Respuesta del asistente virtual: tipo, texto (sin HTML), tema respondido, opciones para continuar la conversación y enlace opcional a una pantalla permitida para el rol.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.util.List;

/**
 * Respuesta del asistente.
 *
 * @param tipo BIENVENIDA, RESPUESTA, PRODUCTO, TASA, ACLARACION o NO_ENTENDIDO
 * @param texto texto plano; los saltos de línea separan párrafos y pasos
 * @param nodo id del tema respondido (nulo en respuestas con datos o sin coincidencia)
 * @param opciones temas sugeridos para continuar
 * @param enlace pantalla relacionada, solo si el rol puede abrirla
 */
public record AsistenteRespuesta(String tipo, String texto, String nodo, List<Opcion> opciones, Enlace enlace) {

    /** Saludo con el menú principal. */
    public static final String BIENVENIDA = "BIENVENIDA";
    /** Respuesta de un tema de la base de conocimiento. */
    public static final String RESPUESTA = "RESPUESTA";
    /** Precio y existencias de productos consultados en el inventario. */
    public static final String PRODUCTO = "PRODUCTO";
    /** Tasa de cambio vigente. */
    public static final String TASA = "TASA";
    /** Varios temas igual de probables: se pide elegir. */
    public static final String ACLARACION = "ACLARACION";
    /** Sin coincidencia: se sugieren temas. */
    public static final String NO_ENTENDIDO = "NO_ENTENDIDO";

    /** Normaliza la lista de opciones. */
    public AsistenteRespuesta {
        opciones = opciones == null ? List.of() : List.copyOf(opciones);
    }

    /**
     * Tema sugerido.
     *
     * @param id id del nodo
     * @param titulo título en el idioma del usuario
     */
    public record Opcion(String id, String titulo) {
    }

    /**
     * Enlace a una pantalla de la aplicación.
     *
     * @param ruta ruta del frontend
     * @param texto texto del botón
     */
    public record Enlace(String ruta, String texto) {
    }
}
