/*
 * Nombre: AsistenteMensajeRequest.java
 * Descripción: Mensaje enviado al asistente virtual: una pregunta en texto libre o el id de un tema elegido en el árbol de decisión.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mensaje al asistente. Se envía la pregunta ({@code texto}) o el tema elegido ({@code nodo}); si llegan ambos,
 * manda el tema.
 *
 * @param texto pregunta en texto libre (máximo 300 caracteres, sin &lt; ni &gt;)
 * @param nodo id de un tema de la base de conocimiento
 */
public record AsistenteMensajeRequest(
        @Size(max = 300, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String texto,
        @Pattern(regexp = "^[a-z0-9-]{1,40}$", message = "{validacion.asistente.tema}") String nodo) {
}
