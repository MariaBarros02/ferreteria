/*
 * Nombre: Montos.java
 * Descripción: Cálculo monetario de líneas y documentos (subtotal, descuento, impuesto, total) con redondeo HALF_UP a 2 decimales.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Reglas de cálculo de importes. El precio unitario es antes de impuestos; el descuento se aplica sobre
 * el subtotal de la línea y el IVA sobre la base después del descuento:
 * <pre>
 * subtotal  = round(cantidad × precio, 2)
 * descuento = round(subtotal × %desc / 100, 2)
 * impuesto  = round((subtotal − descuento) × %iva / 100, 2)
 * total     = subtotal − descuento + impuesto
 * </pre>
 * Los totales del documento son la suma de los valores ya redondeados de cada línea.
 */
public final class Montos {

    /** Escala monetaria (centavos). */
    public static final int ESCALA = 2;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private Montos() { }

    /**
     * Importes de una línea.
     *
     * @param subtotal cantidad × precio
     * @param descuento valor del descuento
     * @param impuesto valor del impuesto
     * @param total total de la línea
     */
    public record Linea(BigDecimal subtotal, BigDecimal descuento, BigDecimal impuesto, BigDecimal total) {
        /** @return base gravable (subtotal − descuento) */
        public BigDecimal base() {
            return subtotal.subtract(descuento);
        }
    }

    /**
     * Totales de un documento.
     *
     * @param subtotal suma de subtotales
     * @param descuentos suma de descuentos
     * @param impuestos suma de impuestos
     * @param total suma de totales
     */
    public record Totales(BigDecimal subtotal, BigDecimal descuentos, BigDecimal impuestos, BigDecimal total) { }

    /**
     * Calcula los importes de una línea.
     *
     * @param cantidad cantidad en la unidad del precio
     * @param precioUnitario precio antes de impuestos
     * @param porcentajeDescuento porcentaje 0–100 (nulo = 0)
     * @param porcentajeIva porcentaje 0–100 (nulo = 0)
     * @return importes redondeados
     */
    public static Linea linea(BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal porcentajeDescuento,
                              BigDecimal porcentajeIva) {
        BigDecimal subtotal = redondear(cantidad.multiply(precioUnitario));
        BigDecimal descuento = redondear(subtotal.multiply(nz(porcentajeDescuento)).divide(CIEN));
        BigDecimal impuesto = redondear(subtotal.subtract(descuento).multiply(nz(porcentajeIva)).divide(CIEN));
        return new Linea(subtotal, descuento, impuesto, subtotal.subtract(descuento).add(impuesto));
    }

    /**
     * Suma los importes de varias líneas.
     *
     * @param lineas líneas calculadas
     * @return totales
     */
    public static Totales totales(Collection<Linea> lineas) {
        BigDecimal s = BigDecimal.ZERO.setScale(ESCALA);
        BigDecimal d = s;
        BigDecimal i = s;
        BigDecimal t = s;
        for (Linea l : lineas) {
            s = s.add(l.subtotal());
            d = d.add(l.descuento());
            i = i.add(l.impuesto());
            t = t.add(l.total());
        }
        return new Totales(s, d, i, t);
    }

    /**
     * Valor proporcional de una parte de una línea (devoluciones parciales).
     *
     * @param totalLinea total de la línea original
     * @param cantidadLinea cantidad de la línea original
     * @param cantidadParte cantidad de la parte
     * @return valor redondeado
     */
    public static BigDecimal proporcional(BigDecimal totalLinea, BigDecimal cantidadLinea, BigDecimal cantidadParte) {
        if (cantidadLinea.signum() == 0) {
            return BigDecimal.ZERO.setScale(ESCALA);
        }
        return totalLinea.multiply(cantidadParte).divide(cantidadLinea, ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * Redondea a centavos con HALF_UP.
     *
     * @param v valor
     * @return valor redondeado
     */
    public static BigDecimal redondear(BigDecimal v) {
        return v.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
