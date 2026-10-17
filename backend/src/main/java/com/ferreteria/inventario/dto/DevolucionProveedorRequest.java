/*
 * Nombre: DevolucionProveedorRequest.java
 * Descripción: DTO de entrada para registrar una devolución a proveedor vinculada a una compra (RF-13, RN-04).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TratamientoDevolucion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Devolución a proveedor.
 *
 * @param compraId compra original
 * @param motivo motivo
 * @param tratamiento normalmente nota crédito del proveedor, cambio o ninguno
 * @param observaciones observaciones
 * @param idOperacion clave de idempotencia
 * @param lineas líneas devueltas
 */
public record DevolucionProveedorRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long compraId,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String motivo,
        @NotNull(message = "{validacion.obligatorio}") TratamientoDevolucion tratamiento,
        @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observaciones,
        @Size(max = 64, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.ID_OPERACION, message = "{validacion.id.operacion}") String idOperacion,
        @NotEmpty(message = "{validacion.lineas.minimo}") @Size(max = 200, message = "{validacion.longitud}")
        List<@Valid @NotNull(message = "{validacion.obligatorio}") Linea> lineas) {

    /**
     * Línea devuelta al proveedor.
     *
     * @param idLineaCompra línea de la compra original
     * @param cantidad cantidad en unidad base
     * @param origen saldo de origen: DISPONIBLE o DANADO
     * @param idLote lote (obligatorio para productos por lote cuando el origen es disponible)
     */
    public record Linea(
            @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long idLineaCompra,
            @NotNull(message = "{validacion.obligatorio}")
            @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
            @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
            @NotNull(message = "{validacion.obligatorio}") EstadoInventario origen,
            @Positive(message = "{validacion.positivo}") Long idLote) {
    }
}
