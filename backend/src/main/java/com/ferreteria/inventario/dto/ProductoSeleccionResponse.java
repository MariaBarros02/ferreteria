/*
 * Nombre: ProductoSeleccionResponse.java
 * Descripción: DTO compacto para los selectores de producto (venta, compra, inventario): existencias, precio vigente y presentaciones.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoRastreo;
import java.math.BigDecimal;
import java.util.List;

/**
 * Información útil antes de seleccionar un producto.
 *
 * @param id identificador
 * @param nombre nombre
 * @param marca marca
 * @param presentacion presentación
 * @param codigoBarras código de barras
 * @param unidadBase unidad base
 * @param tipoRastreo rastreo
 * @param tieneVencimiento vence
 * @param activo activo
 * @param porcentajeIva IVA
 * @param precioVigente precio aprobado vigente (nulo si no tiene: no se puede vender)
 * @param disponible saldo disponible
 * @param reservado saldo reservado
 * @param vencidoSinBaja unidades en lotes vencidos aún no dadas de baja (no vendibles)
 * @param stockMinimo existencia mínima
 * @param unidades presentaciones configuradas
 */
public record ProductoSeleccionResponse(Long id, String nombre, String marca, String presentacion, String codigoBarras,
                                        String unidadBase, TipoRastreo tipoRastreo, boolean tieneVencimiento,
                                        boolean activo, BigDecimal porcentajeIva, BigDecimal precioVigente,
                                        BigDecimal disponible, BigDecimal reservado, BigDecimal vencidoSinBaja,
                                        BigDecimal stockMinimo, List<ConversionResponse> unidades) {
}
