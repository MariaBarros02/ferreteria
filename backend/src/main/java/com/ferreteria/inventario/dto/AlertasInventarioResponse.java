/*
 * Nombre: AlertasInventarioResponse.java
 * Descripción: DTO con las alertas de inventario calculadas con fechas reales y un umbral configurable (RF-05).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Alertas de inventario.
 *
 * @param fechaReferencia fecha usada para el cálculo (zona del negocio)
 * @param diasUmbral días de anticipación usados
 * @param vencidos lotes vencidos con saldo disponible (no vendibles; dar de baja)
 * @param porVencer lotes que vencen dentro del umbral
 * @param agotados productos activos sin disponible
 * @param stockBajo productos con disponible menor o igual al mínimo
 */
public record AlertasInventarioResponse(LocalDate fechaReferencia, int diasUmbral, List<LoteResponse> vencidos,
                                        List<LoteResponse> porVencer, List<ExistenciaResponse> agotados,
                                        List<ExistenciaResponse> stockBajo) {
}
