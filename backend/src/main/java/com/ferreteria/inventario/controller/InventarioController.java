/*
 * Nombre: InventarioController.java
 * Descripción: API REST de inventario (RF-05): existencias, alertas, lotes, cambios de estado, bajas por vencimiento y reservas.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.*;
import com.ferreteria.inventario.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Inventario: todos los roles consultan (los costos solo Inventario/Administrador/Gerencia); Inventario y Administrador ejecutan transiciones.
 */
@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
@Validated
@Tag(name = "Inventario", description = "Existencias, vencimientos, estados y reservas")
public class InventarioController {

    private static final String TRANSICION = "hasAnyRole('INVENTARIO','ADMINISTRADOR')";

    private final InventarioService servicio;
    private final ReservaService reservas;
    private final AuditoriaService auditoria;
    private final ContextoSolicitud contexto;
    private final com.ferreteria.inventario.config.ParametrosNegocio parametros;

    /** Existencias por estado con alertas. */
    @GetMapping("/existencias")
    @Operation(summary = "Existencias por producto", description = "Disponible, reservado, dañado, vencido sin baja, por vencer y alertas.")
    public PaginaResponse<ExistenciaResponse> existencias(@RequestParam(required = false) @Size(max = 100) String q,
                                                          @RequestParam(required = false) @Min(0) @Max(365) Integer dias,
                                                          @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        var r = servicio.existencias(q, dias, pagina, tamano);
        if (veCostos()) {
            return r;
        }
        return new PaginaResponse<>(r.contenido().stream().map(ExistenciaResponse::sinCosto).toList(), r.pagina(),
                r.tamano(), r.totalElementos(), r.totalPaginas());
    }

    /** Alertas de vencimiento, agotados y stock bajo. */
    @GetMapping("/alertas")
    @Operation(summary = "Alertas de inventario", description = "Umbral configurable (app.inventario.dias-alerta-vencimiento) o por parámetro 'dias'.")
    public AlertasInventarioResponse alertas(@RequestParam(required = false) @Min(0) @Max(365) Integer dias) {
        var a = servicio.alertas(dias);
        if (veCostos()) {
            return a;
        }
        return new AlertasInventarioResponse(a.fechaReferencia(), a.diasUmbral(), a.vencidos(), a.porVencer(),
                a.agotados().stream().map(ExistenciaResponse::sinCosto).toList(),
                a.stockBajo().stream().map(ExistenciaResponse::sinCosto).toList());
    }

    /** Lotes de un producto. */
    @GetMapping("/productos/{idProducto}/lotes")
    @Operation(summary = "Lotes de un producto con su vencimiento")
    public List<LoteResponse> lotes(@PathVariable Long idProducto) {
        return servicio.lotes(idProducto);
    }

    /** Historial de movimientos de un producto (kardex). */
    @GetMapping("/productos/{idProducto}/movimientos")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Movimientos de un producto (kardex)")
    public PaginaResponse<MovimientoResponse> movimientos(@PathVariable Long idProducto,
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.TipoMovimiento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return auditoria.consultarFiltrado(new AuditoriaService.Filtros(idProducto, tipo, null, null, desde, hasta),
                parametros.getZonaHoraria(), pagina, tamano);
    }

    /** Disponible ↔ dañado (RN-04). */
    @PostMapping("/cambios-estado")
    @PreAuthorize(TRANSICION)
    @Operation(summary = "Cambiar estado de unidades", description = "Disponible → dañado o dañado → disponible, con motivo.")
    public InventarioResponse cambiarEstado(@Valid @RequestBody CambioEstadoRequest req) {
        return servicio.cambiarEstado(req, contexto.actor());
    }

    /** Baja de un lote vencido. */
    @PostMapping("/lotes/{idLote}/baja-vencimiento")
    @PreAuthorize(TRANSICION)
    @Operation(summary = "Dar de baja un lote vencido", description = "Solo afecta al lote indicado (RN-05).")
    public LoteResponse bajaVencimiento(@PathVariable Long idLote) {
        return servicio.darDeBajaLote(idLote, contexto.actor());
    }

    /** Crea una reserva. */
    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VENTAS','ADMINISTRADOR')")
    @Operation(summary = "Reservar inventario para un cliente")
    public ReservaResponse reservar(@Valid @RequestBody ReservaRequest req) {
        return reservas.crear(req, contexto.actor());
    }

    /** Reservas con filtros. */
    @GetMapping("/reservas")
    @PreAuthorize("hasAnyRole('VENTAS','INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Listar reservas")
    public PaginaResponse<ReservaResponse> listarReservas(
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.EstadoReserva estado,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return reservas.listar(estado, productoId, clienteId, pagina, tamano);
    }

    /** Cancela una reserva (liberación autorizada). */
    @PostMapping("/reservas/{id}/cancelar")
    @PreAuthorize("hasAnyRole('VENTAS','INVENTARIO','ADMINISTRADOR')")
    @Operation(summary = "Cancelar reserva")
    public ReservaResponse cancelarReserva(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return reservas.cancelar(id, req.motivo(), contexto.actor());
    }

    /** Libera de inmediato las reservas vencidas (además de la tarea diaria). */
    @PostMapping("/reservas/liberar-vencidas")
    @PreAuthorize(TRANSICION)
    @Operation(summary = "Liberar reservas vencidas")
    public java.util.Map<String, Integer> liberarVencidas() {
        return java.util.Map.of("liberadas", reservas.liberarVencidas(contexto.actor()));
    }

    private boolean veCostos() {
        return contexto.tieneRol("INVENTARIO", "ADMINISTRADOR", "GERENCIA");
    }
}
