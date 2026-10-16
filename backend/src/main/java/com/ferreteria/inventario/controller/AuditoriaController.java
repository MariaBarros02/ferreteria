/*
 * Nombre: AuditoriaController.java
 * Descripción: API de consulta de auditoría de movimientos de inventario (RF-08); roles Inventario, Administrador y Gerencia.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.controller;

import org.springframework.web.bind.annotation.PathVariable;
import com.ferreteria.inventario.service.Auditables;
import com.ferreteria.inventario.service.EventoAuditoriaService;
import com.ferreteria.inventario.dto.EventoAuditoriaResponse;
import com.ferreteria.inventario.dto.MovimientoResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de movimientos auditados (solo lectura: RN-16).
 */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Historial inmutable de movimientos de inventario")
public class AuditoriaController {

    private final AuditoriaService servicio;
    private final EventoAuditoriaService eventos;
    private final com.ferreteria.inventario.config.ParametrosNegocio parametros;

    /**
     * Consulta paginada.
     *
     * @param idProducto filtro opcional
     * @param pagina página base 0
     * @param tamano tamaño (máx. 100)
     * @return página de movimientos
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Consultar movimientos auditados")
    public PaginaResponse<MovimientoResponse> consultar(@RequestParam(required = false) Long idProducto,
                                                        @RequestParam(defaultValue = "0") int pagina,
                                                        @RequestParam(defaultValue = "20") int tamano) {
        return servicio.consultar(idProducto, pagina, tamano);
    }

    /**
     * Movimientos con filtros combinables (RF-20).
     *
     * @return página de movimientos
     */
    @GetMapping("/movimientos")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Consultar movimientos con filtros",
            description = "Filtros: producto, tipo, usuario, documento (texto) y rango de fechas (zona del negocio).")
    public PaginaResponse<MovimientoResponse> movimientos(
            @RequestParam(required = false) Long idProducto,
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.TipoMovimiento tipo,
            @RequestParam(required = false) Integer idUsuario,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 100) String documento,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int pagina,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int tamano) {
        return servicio.consultarFiltrado(new AuditoriaService.Filtros(idProducto, tipo, idUsuario, documento, desde, hasta),
                parametros.getZonaHoraria(), pagina, tamano);
    }

    /**
     * Eventos de auditoría general (RF-20): creación, actualización, eliminación lógica, ventas, devoluciones,
     * ajustes, recepciones, aprobaciones, cierres, cambios de permisos e importaciones.
     *
     * @return página de eventos
     */
    @GetMapping("/eventos")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Consultar eventos de auditoría",
            description = "Filtros: fechas, usuario (correo), módulo, acción, entidad y búsqueda por identificador u operación.")
    public PaginaResponse<EventoAuditoriaResponse> eventos(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 150) String usuario,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 30) String modulo,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 40) String accion,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 40) String entidad,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int pagina,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int tamano) {
        return eventos.consultar(new EventoAuditoriaService.Filtros(desde, hasta, usuario, modulo, accion, entidad, q),
                pagina, tamano);
    }

    /**
     * Detalle de un evento.
     *
     * @param id evento
     * @return evento
     */
    @GetMapping("/eventos/{id}")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Detalle de un evento de auditoría")
    public EventoAuditoriaResponse evento(@PathVariable Long id) {
        return eventos.obtener(id);
    }

    /**
     * Catálogo de módulos y acciones para los filtros.
     *
     * @return módulos y acciones
     */
    @GetMapping("/catalogo")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Módulos y acciones auditables")
    public java.util.Map<String, java.util.List<String>> catalogo() {
        return java.util.Map.of("modulos", Auditables.MODULOS, "acciones", Auditables.ACCIONES);
    }
}
