/*
 * Nombre: ImportacionResponse.java
 * Descripción: DTO del resultado de validar o importar un archivo de Excel (RF-10): resumen y detalle por fila.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoImportacion;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Importación.
 *
 * @param id identificador
 * @param tipo productos o terceros
 * @param nombreArchivo archivo cargado
 * @param estado validada, importada o cancelada
 * @param totalFilas filas leídas
 * @param filasValidas filas válidas
 * @param filasError filas con errores
 * @param filasDuplicadas filas que ya existen o son posibles duplicados
 * @param filasImportadas filas importadas (tras confirmar)
 * @param usuario correo de quien cargó
 * @param fechaCarga fecha de carga
 * @param fechaProceso fecha de confirmación o cancelación
 * @param filas detalle por fila
 */
public record ImportacionResponse(Long id, String tipo, String nombreArchivo, EstadoImportacion estado, int totalFilas,
                                  int filasValidas, int filasError, int filasDuplicadas, Integer filasImportadas,
                                  String usuario, LocalDateTime fechaCarga, LocalDateTime fechaProceso, List<Fila> filas) {

    /**
     * Fila del archivo.
     *
     * @param fila número de fila en Excel (la 1 es el encabezado)
     * @param estado VALIDA, ERROR, DUPLICADO, POSIBLE_DUPLICADO, IMPORTADA u OMITIDA
     * @param datos valores leídos por columna
     * @param errores errores por columna
     * @param coincidencia registro existente similar (posibles duplicados)
     */
    public record Fila(int fila, String estado, Map<String, String> datos, List<ErrorCelda> errores, String coincidencia) { }

    /**
     * Error de una celda.
     *
     * @param columna columna
     * @param mensaje mensaje
     */
    public record ErrorCelda(String columna, String mensaje) { }
}
