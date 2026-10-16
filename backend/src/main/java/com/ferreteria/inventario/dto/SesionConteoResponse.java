/*
 * Nombre: SesionConteoResponse.java
 * Descripción: DTO de salida de una sesión de conteo con sus líneas y diferencias (RF-15).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoConteo;
import com.ferreteria.inventario.domain.enums.EstadoSesionConteo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sesión de conteo.
 *
 * @param id identificador
 * @param numero consecutivo
 * @param estado abierta, cerrada, aprobada o rechazada
 * @param observaciones observaciones
 * @param usuario correo de quien la abrió
 * @param fechaInicio inicio
 * @param fechaCierre cierre del registro
 * @param aprobador correo de quien decidió
 * @param fechaDecision fecha de decisión
 * @param motivoRechazo motivo del rechazo
 * @param productosContados número de productos contados
 * @param productosConDiferencia número de productos con diferencia
 * @param lineas conteos
 */
public record SesionConteoResponse(Long id, String numero, EstadoSesionConteo estado, String observaciones, String usuario,
                                   LocalDateTime fechaInicio, LocalDateTime fechaCierre, String aprobador,
                                   LocalDateTime fechaDecision, String motivoRechazo, int productosContados,
                                   int productosConDiferencia, List<Linea> lineas) {

    /**
     * Conteo de un producto.
     *
     * @param id identificador
     * @param productoId producto
     * @param producto nombre
     * @param codigoBarras código
     * @param cantidadSistema saldo del sistema al contar
     * @param cantidadContada cantidad contada
     * @param diferencia contado − sistema
     * @param saldoActual saldo disponible actual
     * @param estado estado de la línea
     * @param observacion observación
     * @param ajuste número del ajuste generado
     */
    public record Linea(Long id, Long productoId, String producto, String codigoBarras, BigDecimal cantidadSistema,
                        BigDecimal cantidadContada, BigDecimal diferencia, BigDecimal saldoActual, EstadoConteo estado,
                        String observacion, String ajuste) { }
}
