/*
 * Nombre: DevolucionClienteRequest.java
 * Descripción: DTO de entrada para registrar una devolución de cliente vinculada a una venta (RF-13).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoProductoDevuelto;
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
 * Devolución de cliente.
 *
 * @param ventaId venta original
 * @param motivo motivo
 * @param tratamiento reembolso, saldo a favor, cambio o ninguno
 * @param observaciones observaciones
 * @param idOperacion clave de idempotencia
 * @param lineas líneas devueltas
 */
public record DevolucionClienteRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long ventaId,
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
     * Línea devuelta.
     *
     * @param idLineaVenta línea de la venta original
     * @param cantidad cantidad devuelta en unidad base
     * @param estadoProducto apto (vuelve a disponible), dañado o vencido (van a no apto)
     */
    public record Linea(
            @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long idLineaVenta,
            @NotNull(message = "{validacion.obligatorio}")
            @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
            @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
            @NotNull(message = "{validacion.obligatorio}") EstadoProductoDevuelto estadoProducto) {
    }
}
