/*
 * Nombre: AjusteRequest.java
 * Descripción: DTO de entrada para solicitar un ajuste de inventario (RF-14).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoAjuste;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Solicitud de ajuste.
 *
 * @param productoId producto
 * @param idLote lote (opcional)
 * @param tipo ENTRADA o SALIDA
 * @param cantidad cantidad positiva en unidad base
 * @param motivo motivo obligatorio
 * @param observaciones observaciones
 * @param idMovimientoOriginal movimiento que se rectifica (nota de ajuste, RN-16)
 * @param idOperacion clave de idempotencia
 */
public record AjusteRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
        @Positive(message = "{validacion.positivo}") Long idLote,
        @NotNull(message = "{validacion.obligatorio}") TipoAjuste tipo,
        @NotNull(message = "{validacion.obligatorio}")
        @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
        @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String motivo,
        @Size(max = 500, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observaciones,
        @Positive(message = "{validacion.positivo}") Long idMovimientoOriginal,
        @Size(max = 64, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.ID_OPERACION, message = "{validacion.id.operacion}") String idOperacion) {
}
