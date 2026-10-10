/*
 * Nombre: ProductoResponse.java
 * Descripción: DTO de salida de producto.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Producto;

/**
 * Representación pública de una referencia de producto.
 */
public record ProductoResponse(Long id, String nombre, String marca, String presentacion, String codigoBarras,
                               String unidadBase, TipoRastreo tipoRastreo, boolean tieneVencimiento,
                               boolean tieneGarantia) {

    /** Construye desde la entidad. */
    public static ProductoResponse de(Producto p) {
        return new ProductoResponse(p.getId(), p.getNombre(), p.getMarca(), p.getPresentacion(), p.getCodigoBarras(),
                p.getUnidadBase(), p.getTipoRastreo(), p.isTieneVencimiento(), p.isTieneGarantia());
    }
}
