/*
 * Nombre: CompraResponse.java
 * Descripción: DTO de salida de una compra con líneas, avance de recepción, totales y documentos de ingreso (RF-03, RF-17).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.TipoRastreo;

/**
 * Compra con su detalle.
 *
 * @param id identificador
 * @param numero consecutivo
 * @param proveedorId proveedor
 * @param proveedor nombre del proveedor
 * @param proveedorIdentificacion identificación del proveedor
 * @param fecha fecha de registro
 * @param estado activo o anulado
 * @param estadoRecepcion PENDIENTE, PARCIAL, RECIBIDA o ANULADA
 * @param usuario correo de quien registró
 * @param observaciones observaciones
 * @param motivoAnulacion motivo de anulación
 * @param lineas líneas
 * @param subtotal subtotal
 * @param descuentos descuentos
 * @param impuestos impuestos
 * @param total total
 * @param documentos documentos de ingreso
 */
public record CompraResponse(Long id, String numero, Long proveedorId, String proveedor, String proveedorIdentificacion,
                             LocalDateTime fecha, EstadoDocumento estado, String estadoRecepcion, String usuario,
                             String observaciones, String motivoAnulacion, List<Linea> lineas, BigDecimal subtotal,
                             BigDecimal descuentos, BigDecimal impuestos, BigDecimal total, List<Documento> documentos) {

    /**
     * Línea de compra.
     *
     * @param id identificador de la línea
     * @param productoId producto
     * @param producto nombre legible
     * @param codigoBarras código de barras
     * @param unidadBase unidad base
     * @param tipoRastreo rastreo del producto
     * @param tieneVencimiento el producto vence
     * @param cantidadPedida pedida (unidad base)
     * @param cantidadRecibida recibida (unidad base)
     * @param cantidadPendiente pendiente (unidad base)
     * @param unidad presentación pedida
     * @param cantidadPresentacion cantidad en la presentación
     * @param costoPresentacion costo por presentación
     * @param costoUnitario costo por unidad base
     * @param porcentajeIva IVA
     * @param porcentajeDescuento descuento
     * @param subtotal subtotal
     * @param descuento descuento
     * @param impuesto impuesto
     * @param total total
     */
    public record Linea(Long id, Long productoId, String producto, String codigoBarras, String unidadBase,
                        TipoRastreo tipoRastreo, boolean tieneVencimiento, BigDecimal cantidadPedida,
                        BigDecimal cantidadRecibida, BigDecimal cantidadPendiente, String unidad,
                        BigDecimal cantidadPresentacion, BigDecimal costoPresentacion, BigDecimal costoUnitario,
                        BigDecimal porcentajeIva, BigDecimal porcentajeDescuento, BigDecimal subtotal,
                        BigDecimal descuento, BigDecimal impuesto, BigDecimal total) { }

    /**
     * Documento de ingreso.
     *
     * @param id identificador
     * @param consecutivo consecutivo
     * @param estado activo o anulado
     * @param facturaProveedor factura del proveedor
     * @param fecha fecha
     * @param usuario correo de quien recibió
     * @param observaciones observaciones
     * @param motivoAnulacion motivo de anulación
     * @param recepciones cantidades recibidas
     */
    public record Documento(Long id, String consecutivo, EstadoDocumento estado, String facturaProveedor,
                            LocalDateTime fecha, String usuario, String observaciones, String motivoAnulacion,
                            List<Recepcion> recepciones) { }

    /**
     * Recepción de una línea.
     *
     * @param id identificador
     * @param idLineaCompra línea
     * @param productoId producto
     * @param producto nombre
     * @param cantidad cantidad recibida
     * @param lote código del lote
     * @param fechaVencimiento vencimiento del lote
     * @param costoUnitario costo neto por unidad base
     */
    public record Recepcion(Long id, Long idLineaCompra, Long productoId, String producto, BigDecimal cantidad,
                            String lote, LocalDate fechaVencimiento, BigDecimal costoUnitario) { }
}
