/*
 * Nombre: VentaDetalleResponse.java
 * Descripción: DTO del detalle completo de una venta para consulta y comprobante (RF-18, RF-19): cliente, usuario, líneas, devoluciones y trazabilidad.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.util.List;

/**
 * Detalle de una venta obtenido exclusivamente de datos persistidos.
 *
 * @param venta venta con líneas, totales y factura
 * @param cliente datos del cliente
 * @param usuarioNombre nombre del usuario responsable (nulo en ventas anteriores a la v1.1)
 * @param usuarioCorreo correo del usuario responsable
 * @param empresa datos del negocio emisor
 * @param devoluciones devoluciones relacionadas
 * @param eventos eventos de auditoría de la venta
 * @param movimientos movimientos de inventario generados por la venta
 * @param exportable {@code true} si el comprobante está completo y se puede imprimir/exportar
 * @param motivosNoExportable razones por las que no se puede exportar
 * @param leyenda leyenda legal del documento
 */
public record VentaDetalleResponse(VentaResponse venta, TerceroResponse cliente, String usuarioNombre, String usuarioCorreo,
                                   EmpresaResponse empresa, List<DevolucionResponse> devoluciones,
                                   List<EventoAuditoriaResponse> eventos, List<MovimientoResponse> movimientos,
                                   boolean exportable, List<String> motivosNoExportable, String leyenda) {
}
