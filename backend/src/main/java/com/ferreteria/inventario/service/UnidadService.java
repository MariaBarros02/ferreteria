/*
 * Nombre: UnidadService.java
 * Descripción: Conversión de cantidades de una presentación (caja, paquete…) a la unidad base del producto con validación de precisión (RF-02).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.model.ConversionUnidad;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ConversionUnidadRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Toda cantidad de compra o venta se convierte a la unidad base antes de afectar el saldo (RF-02).
 */
@Service
@RequiredArgsConstructor
public class UnidadService {

    private final ConversionUnidadRepository conversiones;

    /**
     * Resultado de la conversión.
     *
     * @param cantidadBase cantidad en unidad base
     * @param factor factor aplicado (1 para la unidad base)
     * @param unidad presentación usada (nula = unidad base)
     */
    public record Conversion(BigDecimal cantidadBase, BigDecimal factor, String unidad) { }

    /**
     * Convierte una cantidad a unidad base.
     *
     * @param p producto
     * @param cantidad cantidad en la presentación indicada
     * @param unidad presentación (nula, vacía o igual a la unidad base = sin conversión)
     * @return conversión
     * @throws ApiException 422 si la presentación no está configurada o la precisión no es válida
     */
    public Conversion aUnidadBase(Producto p, BigDecimal cantidad, String unidad) {
        if (unidad == null || unidad.isBlank() || unidad.trim().equalsIgnoreCase(p.getUnidadBase())) {
            return new Conversion(cantidad, BigDecimal.ONE, null);
        }
        ConversionUnidad c = conversiones
                .findByIdProductoAndUnidadPresentacionIgnoreCase(p.getId(), unidad.trim())
                .orElseThrow(() -> ApiException.regla("CONVERSION_NO_CONFIGURADA", "error.conversion.no.configurada",
                        unidad, p.getNombre()));
        int decimales = Math.max(cantidad.stripTrailingZeros().scale(), 0);
        if (decimales > c.getPrecisionDecimales()) {
            throw ApiException.regla("PRECISION_INVALIDA", "error.precision.invalida",
                    c.getPrecisionDecimales(), c.getUnidadPresentacion());
        }
        return new Conversion(cantidad.multiply(c.getFactorAUnidadBase()), c.getFactorAUnidadBase(),
                c.getUnidadPresentacion());
    }

    /**
     * Convierte un valor unitario de la presentación a la unidad base (costo por caja → costo por unidad).
     *
     * @param valorPresentacion valor por presentación
     * @param factor factor de conversión
     * @return valor por unidad base (4 decimales)
     */
    public static BigDecimal porUnidadBase(BigDecimal valorPresentacion, BigDecimal factor) {
        return valorPresentacion.divide(factor, 4, RoundingMode.HALF_UP);
    }
}
