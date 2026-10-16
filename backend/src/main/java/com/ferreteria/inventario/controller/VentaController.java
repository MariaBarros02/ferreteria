/*
 * Nombre: VentaController.java
 * Descripción: API REST de ventas (RF-04, RF-18, RF-19): registro con importes calculados en el servidor, listado con filtros y detalle completo para el comprobante.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.dto.EmpresaResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.VentaDetalleResponse;
import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.dto.VentaResponse;
import com.ferreteria.inventario.dto.VentaResumenResponse;
import com.ferreteria.inventario.service.VentaConsultaService;
import com.ferreteria.inventario.service.VentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Operaciones de venta.
 */
@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ventas", description = "Registro de ventas, consulta del detalle y datos del comprobante")
public class VentaController {

    private static final String LECTURA = "hasAnyRole('VENTAS','INVENTARIO','ADMINISTRADOR','GERENCIA')";

    private final VentaService servicio;
    private final VentaConsultaService consultas;

    /** Registra una venta. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VENTAS','ADMINISTRADOR')")
    @Operation(summary = "Registrar venta", description = "Descuenta inventario de forma atómica y emite la factura. "
            + "Subtotal, descuentos (por línea, hasta el máximo configurado), IVA y total se calculan en el servidor "
            + "con el precio vigente aprobado. Con idOperacion, un reintento devuelve la misma venta.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Venta y factura creadas"),
                    @ApiResponse(responseCode = "409", description = "Saldo insuficiente (RN-03, RN-11), lote vencido o período cerrado"),
                    @ApiResponse(responseCode = "422", description = "Sin precio vigente, conversión no configurada, descuento no permitido o sin tasa de cambio vigente para la moneda")})
    public VentaResponse registrar(@Valid @RequestBody VentaRequest req, Authentication auth, HttpServletRequest http) {
        return servicio.registrar(req, auth.getName(), http.getRemoteAddr());
    }

    /** Lista paginada con filtros. */
    @GetMapping
    @PreAuthorize(LECTURA)
    @Operation(summary = "Listar ventas", description = "Filtros: número interno (q), cliente, rango de fechas (zona del negocio) y estado.")
    public PaginaResponse<VentaResumenResponse> listar(
            @RequestParam(required = false) @Size(max = 30) String q,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoDocumento estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return consultas.listar(new VentaConsultaService.Filtros(q, clienteId, desde, hasta, estado), pagina, tamano);
    }

    /** Consulta una venta. */
    @GetMapping("/{id}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Consultar venta")
    public VentaResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Detalle completo (RF-18) y datos para previsualizar/exportar el comprobante (RF-19). */
    @GetMapping("/{id}/detalle")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Detalle de la venta y del comprobante",
            description = "Incluye cliente, usuario responsable, líneas con precios, descuentos e IVA persistidos, factura, "
                    + "devoluciones relacionadas, movimientos e historial de auditoría. 'exportable' indica si el "
                    + "comprobante está completo.")
    public VentaDetalleResponse detalle(@PathVariable Long id) {
        return consultas.detalle(id);
    }

    /** Datos del negocio que se imprimen en el comprobante. */
    @GetMapping("/empresa")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Datos del negocio emisor (configurables en app.empresa.*)")
    public EmpresaResponse empresa() {
        return consultas.empresa();
    }
}
