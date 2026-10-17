/*
 * Nombre: DevolucionResponse.java
 * Descripción: DTO de salida de una devolución con sus líneas (RF-13).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.EstadoProductoDevuelto;
import com.ferreteria.inventario.domain.enums.TipoDevolucion;
import com.ferreteria.inventario.domain.enums.TratamientoDevolucion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Devolución registrada.
 *
 * @param id identificador
 * @param numero consecutivo
 * @param tipo cliente o proveedor
 * @param ventaId venta original
 * @param ventaNumero número interno de la venta
 * @param compraId compra original
 * @param compraNumero número de la compra
 * @param tercero cliente o proveedor
 * @param fecha fecha
 * @param usuario correo del responsable
 * @param motivo motivo
 * @param tratamiento tratamiento del importe
 * @param total importe total
 * @param estado estado
 * @param observaciones observaciones
 * @param lineas líneas
 */
public record DevolucionResponse(Long id, String numero, TipoDevolucion tipo, Long ventaId, String ventaNumero, Long compraId,
                                 String compraNumero, String tercero, LocalDateTime fecha, String usuario, String motivo,
                                 TratamientoDevolucion tratamiento, BigDecimal total, EstadoDocumento estado,
                                 String observaciones, List<Linea> lineas) {

    /**
     * Línea devuelta.
     *
     * @param id identificador
     * @param productoId producto
     * @param producto nombre
     * @param cantidad cantidad
     * @param estadoProducto estado del producto devuelto (cliente)
     * @param origen saldo de origen (proveedor)
     * @param lote código del lote
     * @param valor importe
     * @param idLineaVenta línea de venta
     * @param idLineaCompra línea de compra
     */
    public record Linea(Long id, Long productoId, String producto, BigDecimal cantidad, EstadoProductoDevuelto estadoProducto,
                        EstadoInventario origen, String lote, BigDecimal valor, Long idLineaVenta, Long idLineaCompra) { }
}
