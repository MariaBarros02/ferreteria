/*
 * Nombre: ProductoResponse.java
 * Descripción: DTO de salida de una referencia de producto.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Producto;
import java.math.BigDecimal;

/**
 * Representación pública de una referencia de producto. El costo promedio solo se informa a los roles con
 * acceso a precios (ver {@link #sinCosto()}).
 *
 * @param id identificador
 * @param nombre nombre
 * @param marca marca
 * @param presentacion presentación
 * @param codigoBarras código de barras
 * @param unidadBase unidad base
 * @param tipoRastreo rastreo
 * @param tieneVencimiento vence
 * @param tieneGarantia tiene garantía
 * @param activo referencia activa
 * @param descripcion descripción
 * @param categoria categoría
 * @param porcentajeIva IVA
 * @param porcentajeIncremento recargo para la propuesta de precio
 * @param costoPromedio costo promedio ponderado
 * @param stockMinimo existencia mínima
 * @param mesesGarantia meses de garantía
 */
public record ProductoResponse(Long id, String nombre, String marca, String presentacion, String codigoBarras,
                               String unidadBase, TipoRastreo tipoRastreo, boolean tieneVencimiento,
                               boolean tieneGarantia, boolean activo, String descripcion, String categoria,
                               BigDecimal porcentajeIva, BigDecimal porcentajeIncremento, BigDecimal costoPromedio,
                               BigDecimal stockMinimo, Integer mesesGarantia) {
    /** Construye desde la entidad. */
    public static ProductoResponse de(Producto p) {
        return new ProductoResponse(p.getId(), p.getNombre(), p.getMarca(), p.getPresentacion(), p.getCodigoBarras(),
                p.getUnidadBase(), p.getTipoRastreo(), p.isTieneVencimiento(), p.isTieneGarantia(), p.isActivo(),
                p.getDescripcion(), p.getCategoria(), p.getPorcentajeIva(), p.getPorcentajeIncremento(),
                p.getCostoPromedio(), p.getStockMinimo(), p.getMesesGarantia());
    }

    /**
     * Copia sin el costo (para roles sin acceso a costos).
     *
     * @return copia sin costo promedio
     */
    public ProductoResponse sinCosto() {
        return new ProductoResponse(id, nombre, marca, presentacion, codigoBarras, unidadBase, tipoRastreo,
                tieneVencimiento, tieneGarantia, activo, descripcion, categoria, porcentajeIva, porcentajeIncremento,
                null, stockMinimo, mesesGarantia);
    }
}
