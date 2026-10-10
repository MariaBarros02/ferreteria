/*
 * Nombre: AuditoriaController.java
 * Descripción: API de consulta de auditoría de movimientos de inventario (RF-08); roles Inventario, Administrador y Gerencia.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

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
}
