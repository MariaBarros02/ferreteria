/*
 * Nombre: CierreResumenResponse.java
 * Descripción: DTO con el resumen de un período para el cierre mensual: totales, movimientos, conciliación y pendientes (RF-08).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen del período.
 *
 * @param periodo mes (AAAA-MM)
 * @param desde primer día
 * @param hasta último día
 * @param estado ABIERTO, CERRADO o REABIERTO
 * @param numeroVentas ventas activas
 * @param subtotalVentas subtotal
 * @param descuentosVentas descuentos
 * @param impuestosVentas impuestos
 * @param totalVentas total
 * @param numeroDevolucionesClientes devoluciones de clientes
 * @param totalDevolucionesClientes total devuelto por clientes
 * @param numeroDevolucionesProveedor devoluciones a proveedor
 * @param totalDevolucionesProveedor total devuelto a proveedores
 * @param documentosIngreso documentos de ingreso activos
 * @param valorComprasRecibidas valor neto de lo recibido
 * @param movimientos movimientos por tipo y estado
 * @param diferenciasConciliacion productos cuyo saldo no coincide con la suma de sus movimientos
 * @param pendientes operaciones pendientes del período
 * @param bloqueantes pendientes que impiden cerrar
 * @param cierre cierre registrado, si existe
 */
public record CierreResumenResponse(String periodo, LocalDate desde, LocalDate hasta, String estado, long numeroVentas,
                                    BigDecimal subtotalVentas, BigDecimal descuentosVentas, BigDecimal impuestosVentas,
                                    BigDecimal totalVentas, long numeroDevolucionesClientes,
                                    BigDecimal totalDevolucionesClientes, long numeroDevolucionesProveedor,
                                    BigDecimal totalDevolucionesProveedor, long documentosIngreso,
                                    BigDecimal valorComprasRecibidas, List<Movimientos> movimientos,
                                    List<Diferencia> diferenciasConciliacion, Pendientes pendientes,
                                    List<String> bloqueantes, CierreResponse cierre) {

    /**
     * Movimientos agrupados.
     *
     * @param tipo tipo de movimiento
     * @param estadoInventario saldo afectado
     * @param cantidad número de movimientos
     * @param unidades suma neta de unidades
     */
    public record Movimientos(String tipo, String estadoInventario, long cantidad, BigDecimal unidades) { }

    /**
     * Diferencia entre el saldo registrado y el esperado según los movimientos.
     *
     * @param productoId producto
     * @param producto nombre
     * @param saldoRegistrado saldo disponible actual
     * @param saldoEsperado suma de movimientos del saldo disponible
     */
    public record Diferencia(Long productoId, String producto, BigDecimal saldoRegistrado, BigDecimal saldoEsperado) { }

    /**
     * Operaciones pendientes.
     *
     * @param ajustesPendientes ajustes sin decidir solicitados hasta el fin del período
     * @param conteosSinDecidir sesiones de conteo abiertas o cerradas sin decidir
     * @param preciosPendientes propuestas de precio sin decidir (informativo)
     * @param reservasVencidasActivas reservas activas con fecha límite vencida (informativo)
     */
    public record Pendientes(long ajustesPendientes, long conteosSinDecidir, long preciosPendientes,
                             long reservasVencidasActivas) { }
}
