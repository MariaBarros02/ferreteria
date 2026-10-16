/*
 * Nombre: CalculadoraPrecio.java
 * Descripción: Fórmulas de precio sugerido (RF-07): recargo sobre costo (RN-07) o margen sobre precio de venta, con redondeo configurable.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.exception.ApiException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Cálculo puro (sin estado) del precio sugerido antes de impuestos. Se distinguen dos conceptos que no deben
 * confundirse:
 * <ul>
 *   <li><b>Recargo sobre costo</b> (regla aprobada RN-07, modo por defecto): {@code P = C × (1 + r)}.
 *       Ej.: C = 12.000, r = 0,30 → P = 15.600.</li>
 *   <li><b>Margen sobre el precio de venta</b>: {@code P = C / (1 − m)}, con {@code 0 ≤ m < 1}.
 *       Ej.: C = 12.000, m = 0,30 → P = 17.142,86 (antes de redondeo).</li>
 * </ul>
 * El resultado se redondea al múltiplo configurado ({@code app.precios.multiplo-redondeo}) con HALF_UP.
 * El IVA se suma en la venta según el porcentaje del producto; no forma parte del precio sugerido.
 */
public final class CalculadoraPrecio {

    /** Modo recargo sobre costo. */
    public static final String RECARGO = "recargo";
    /** Modo margen sobre precio de venta. */
    public static final String MARGEN = "margen";
    /** Precio fijado manualmente (requiere justificación). */
    public static final String MANUAL = "manual";

    private CalculadoraPrecio() { }

    /**
     * Calcula el precio sugerido.
     *
     * @param costo costo promedio por unidad base (≥ 0)
     * @param porcentaje recargo o margen como fracción (0,30 = 30 %)
     * @param modo {@link #RECARGO} o {@link #MARGEN}
     * @param multiplo múltiplo de redondeo (&gt; 0)
     * @return precio sugerido redondeado
     * @throws ApiException 422 si el margen es ≥ 100 % o los datos son inválidos
     */
    public static BigDecimal calcular(BigDecimal costo, BigDecimal porcentaje, String modo, BigDecimal multiplo) {
        if (costo == null || costo.signum() < 0 || porcentaje == null || porcentaje.signum() < 0) {
            throw ApiException.regla("PRECIO_DATOS_INVALIDOS", "error.precio.datos");
        }
        BigDecimal bruto;
        if (MARGEN.equals(modo)) {
            if (porcentaje.compareTo(BigDecimal.ONE) >= 0) {
                throw ApiException.regla("MARGEN_INVALIDO", "error.precio.margen");
            }
            bruto = costo.divide(BigDecimal.ONE.subtract(porcentaje), MathContext.DECIMAL64);
        } else {
            bruto = costo.multiply(BigDecimal.ONE.add(porcentaje));
        }
        return redondear(bruto, multiplo);
    }

    /**
     * Redondea al múltiplo indicado (HALF_UP).
     *
     * @param valor valor
     * @param multiplo múltiplo (&gt; 0); nulo o no positivo = 2 decimales
     * @return valor redondeado con 4 decimales de escala
     */
    public static BigDecimal redondear(BigDecimal valor, BigDecimal multiplo) {
        if (multiplo == null || multiplo.signum() <= 0) {
            return valor.setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.UNNECESSARY);
        }
        BigDecimal veces = valor.divide(multiplo, 0, RoundingMode.HALF_UP);
        return veces.multiply(multiplo).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Margen resultante sobre el precio: (P − C) / P.
     *
     * @param costo costo
     * @param precio precio
     * @return margen como fracción, o {@code null} si no se puede calcular
     */
    public static BigDecimal margen(BigDecimal costo, BigDecimal precio) {
        if (costo == null || precio == null || precio.signum() <= 0) {
            return null;
        }
        return precio.subtract(costo).divide(precio, 4, RoundingMode.HALF_UP);
    }

    /**
     * Costo promedio ponderado tras una entrada (RN-07).
     *
     * @param existencias unidades en existencia antes de la entrada
     * @param costoActual costo promedio actual (nulo si no hay)
     * @param cantidad unidades que entran
     * @param costoEntrada costo unitario de la entrada
     * @return nuevo costo promedio (4 decimales)
     */
    public static BigDecimal promedioPonderado(BigDecimal existencias, BigDecimal costoActual, BigDecimal cantidad,
                                               BigDecimal costoEntrada) {
        if (costoActual == null || existencias.signum() <= 0) {
            return costoEntrada.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal total = existencias.multiply(costoActual).add(cantidad.multiply(costoEntrada));
        return total.divide(existencias.add(cantidad), 4, RoundingMode.HALF_UP);
    }
}
