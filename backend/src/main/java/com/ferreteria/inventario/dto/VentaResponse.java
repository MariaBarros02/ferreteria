/*
 * Nombre: VentaResponse.java
 * Descripción: DTO de salida de una venta con líneas, totales persistidos y factura (RF-04, RF-18).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.ModalidadFactura;
import com.ferreteria.inventario.domain.model.Factura;
import com.ferreteria.inventario.domain.model.LineaVenta;
import com.ferreteria.inventario.domain.model.Venta;
import com.ferreteria.inventario.service.Montos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Venta registrada. Todos los importes provienen de los valores persistidos (precio aplicado y totales
 * guardados al vender); nunca se recalculan con precios actuales.
 *
 * @param id identificador
 * @param numeroInterno número interno de venta
 * @param clienteId cliente
 * @param fecha fecha (UTC)
 * @param estado estado del documento
 * @param lineas líneas
 * @param total total a pagar
 * @param factura factura emitida
 * @param cliente nombre del cliente
 * @param usuario correo del usuario responsable
 * @param subtotal subtotal antes de descuentos e impuestos
 * @param totalDescuentos total de descuentos
 * @param totalImpuestos total de impuestos
 * @param observaciones observaciones
 * @param moneda moneda del documento (ISO 4217; COP en ventas anteriores a V5)
 * @param tasaCambio pesos colombianos por 1 unidad de la moneda (1 en COP)
 * @param subtotalMoneda subtotal en la moneda del documento
 * @param descuentosMoneda descuentos en la moneda del documento
 * @param impuestosMoneda impuestos en la moneda del documento
 * @param totalMoneda total en la moneda del documento (los demás importes están en COP)
 */
public record VentaResponse(Long id, String numeroInterno, Long clienteId, LocalDateTime fecha, EstadoDocumento estado,
                            List<Linea> lineas, BigDecimal total, FacturaInfo factura, String cliente, String usuario,
                            BigDecimal subtotal, BigDecimal totalDescuentos, BigDecimal totalImpuestos,
                            String observaciones, String moneda, BigDecimal tasaCambio, BigDecimal subtotalMoneda,
                            BigDecimal descuentosMoneda, BigDecimal impuestosMoneda, BigDecimal totalMoneda) {

    /**
     * Línea de la venta (cantidad en unidad base).
     *
     * @param productoId producto
     * @param cantidad cantidad en unidad base
     * @param precioAplicado precio aplicado por unidad base
     * @param subtotal cantidad × precio
     * @param id identificador de la línea
     * @param producto nombre legible del producto
     * @param codigoBarras código de barras
     * @param unidadBase unidad base
     * @param unidad presentación vendida
     * @param cantidadPresentacion cantidad en la presentación vendida
     * @param porcentajeDescuento descuento
     * @param porcentajeIva IVA
     * @param descuento valor del descuento
     * @param impuesto valor del impuesto
     * @param total total de la línea
     * @param garantiaHasta fin de la garantía
     * @param cantidadDevuelta cantidad ya devuelta (solo en el detalle)
     * @param precioMoneda precio unitario en la moneda de la venta
     * @param subtotalMoneda subtotal en la moneda de la venta
     * @param descuentoMoneda descuento en la moneda de la venta
     * @param impuestoMoneda impuesto en la moneda de la venta
     * @param totalMoneda total en la moneda de la venta
     */
    public record Linea(Long productoId, BigDecimal cantidad, BigDecimal precioAplicado, BigDecimal subtotal, Long id,
                        String producto, String codigoBarras, String unidadBase, String unidad,
                        BigDecimal cantidadPresentacion, BigDecimal porcentajeDescuento, BigDecimal porcentajeIva,
                        BigDecimal descuento, BigDecimal impuesto, BigDecimal total, LocalDate garantiaHasta,
                        BigDecimal cantidadDevuelta, BigDecimal precioMoneda, BigDecimal subtotalMoneda,
                        BigDecimal descuentoMoneda, BigDecimal impuestoMoneda, BigDecimal totalMoneda) { }

    /**
     * Datos de la factura emitida.
     *
     * @param id identificador
     * @param numeracionDian numeración fiscal
     * @param modalidad modalidad
     * @param impuestos impuestos
     * @param descuentos descuentos
     * @param fechaEmision fecha de emisión
     * @param validaFiscalmente {@code true} solo si la numeración proviene de una integración DIAN validada
     */
    public record FacturaInfo(Long id, String numeracionDian, ModalidadFactura modalidad, BigDecimal impuestos,
                              BigDecimal descuentos, LocalDateTime fechaEmision, boolean validaFiscalmente) { }

    /**
     * Datos de un producto para mostrar en la línea.
     *
     * @param nombre nombre legible
     * @param codigoBarras código de barras
     * @param unidadBase unidad base
     */
    public record InfoProducto(String nombre, String codigoBarras, String unidadBase) { }

    /**
     * Construye desde las entidades sin datos descriptivos adicionales.
     *
     * @param v venta
     * @param f factura (puede ser nula)
     * @return DTO
     */
    public static VentaResponse de(Venta v, Factura f) {
        return de(v, f, null, null, Map.of(), Map.of(), false);
    }

    /**
     * Construye desde las entidades.
     *
     * @param v venta
     * @param f factura (puede ser nula)
     * @param cliente nombre del cliente
     * @param usuario correo del usuario
     * @param productos datos de los productos por id
     * @param devueltas cantidades devueltas por línea (vacío si no aplica)
     * @param fiscal la numeración es fiscalmente válida
     * @return DTO
     */
    public static VentaResponse de(Venta v, Factura f, String cliente, String usuario, Map<Long, InfoProducto> productos,
                                   Map<Long, BigDecimal> devueltas, boolean fiscal) {
        List<Linea> ls = v.getLineas().stream().map(l -> linea(l, productos.get(l.getIdProducto()),
                devueltas.isEmpty() ? null : devueltas.getOrDefault(l.getId(), BigDecimal.ZERO))).toList();
        BigDecimal subtotal = v.getSubtotal() != null ? v.getSubtotal()
                : ls.stream().map(Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desc = v.getTotalDescuentos() != null ? v.getTotalDescuentos() : f == null ? BigDecimal.ZERO : f.getDescuentos();
        BigDecimal imp = v.getTotalImpuestos() != null ? v.getTotalImpuestos() : f == null ? BigDecimal.ZERO : f.getImpuestos();
        BigDecimal total = v.getTotal() != null ? v.getTotal() : subtotal.subtract(desc).add(imp);
        FacturaInfo fi = f == null ? null
                : new FacturaInfo(f.getId(), f.getNumeracionDian(), f.getModalidad(), f.getImpuestos(), f.getDescuentos(),
                        f.getFechaEmision(), fiscal);
        String moneda = v.getMoneda() == null ? "COP" : v.getMoneda();
        BigDecimal tasa = v.getTasaCambio() == null ? BigDecimal.ONE : v.getTasaCambio();
        return new VentaResponse(v.getId(), v.getNumeroInterno(), v.getIdCliente(), v.getFecha(), v.getEstado(), ls, total,
                fi, cliente, usuario, subtotal, desc, imp, v.getObservaciones(), moneda, tasa,
                o(v.getSubtotalMoneda(), subtotal), o(v.getDescuentosMoneda(), desc), o(v.getImpuestosMoneda(), imp),
                o(v.getTotalMoneda(), total));
    }

    private static Linea linea(LineaVenta l, InfoProducto p, BigDecimal devuelta) {
        BigDecimal subtotal = l.getSubtotal() != null ? l.getSubtotal() : Montos.redondear(l.getCantidad().multiply(l.getPrecioAplicado()));
        BigDecimal total = l.getTotal() != null ? l.getTotal() : subtotal.subtract(l.getDescuento()).add(l.getImpuesto());
        return new Linea(l.getIdProducto(), l.getCantidad(), l.getPrecioAplicado(), subtotal, l.getId(),
                p == null ? null : p.nombre(), p == null ? null : p.codigoBarras(), p == null ? null : p.unidadBase(),
                l.getUnidad(), l.getCantidadPresentacion(), l.getPorcentajeDescuento(), l.getPorcentajeIva(),
                l.getDescuento(), l.getImpuesto(), total, l.getGarantiaHasta(), devuelta,
                o(l.getPrecioMoneda(), l.getPrecioAplicado()), o(l.getSubtotalMoneda(), subtotal),
                o(l.getDescuentoMoneda(), l.getDescuento()), o(l.getImpuestoMoneda(), l.getImpuesto()),
                o(l.getTotalMoneda(), total));
    }

    /** Valor en moneda del documento o, en ventas anteriores a V5 (siempre COP), el valor en COP. */
    private static BigDecimal o(BigDecimal moneda, BigDecimal funcional) {
        return moneda != null ? moneda : funcional;
    }
}
