/*
 * Nombre: VentaResponse.java
 * Descripción: DTO de salida de venta con líneas, total y factura emitida (número interno distinto de numeración fiscal, RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.ModalidadFactura;
import com.ferreteria.inventario.domain.model.Factura;
import com.ferreteria.inventario.domain.model.Venta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Venta registrada.
 */
public record VentaResponse(Long id, String numeroInterno, Long clienteId, LocalDateTime fecha, EstadoDocumento estado,
                            List<Linea> lineas, BigDecimal total, FacturaInfo factura) {

    /** Línea de la venta (cantidad en unidad base). */
    public record Linea(Long productoId, BigDecimal cantidad, BigDecimal precioAplicado, BigDecimal subtotal) { }

    /** Datos de la factura emitida. */
    public record FacturaInfo(Long id, String numeracionDian, ModalidadFactura modalidad, BigDecimal impuestos,
                              BigDecimal descuentos) { }

    /**
     * Construye desde las entidades.
     *
     * @param v venta
     * @param f factura (puede ser nula)
     * @return DTO
     */
    public static VentaResponse de(Venta v, Factura f) {
        List<Linea> ls = v.getLineas().stream()
                .map(l -> new Linea(l.getIdProducto(), l.getCantidad(), l.getPrecioAplicado(),
                        l.getCantidad().multiply(l.getPrecioAplicado())))
                .toList();
        BigDecimal total = ls.stream().map(Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        FacturaInfo fi = f == null ? null
                : new FacturaInfo(f.getId(), f.getNumeracionDian(), f.getModalidad(), f.getImpuestos(), f.getDescuentos());
        return new VentaResponse(v.getId(), v.getNumeroInterno(), v.getIdCliente(), v.getFecha(), v.getEstado(), ls, total, fi);
    }
}
