/*
 * Nombre: ConfirmacionImportacionRequest.java
 * Descripción: DTO de entrada para confirmar una importación con la decisión sobre los posibles duplicados (RN-15).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Confirmación.
 *
 * @param importarPosiblesDuplicados filas marcadas como posible duplicado que el usuario decide importar igualmente
 */
public record ConfirmacionImportacionRequest(@Size(max = 5000) List<Integer> importarPosiblesDuplicados) {
}
