/*
 * Nombre: AuditoriaService.java
 * Descripción: Servicio de auditoría: registra movimientos inmutables con los ocho campos de RF-08 y permite consultarlos.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import com.ferreteria.inventario.dto.MovimientoResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.repository.MovimientoInventarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro y consulta de movimientos de inventario.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final MovimientoInventarioRepository movimientos;
    private final Clock reloj;

    /**
     * Registra un movimiento dentro de la transacción en curso (todo o nada, RNF-02).
     *
     * @param tipo tipo de movimiento
     * @param idProducto producto afectado
     * @param cantidad cantidad con signo
     * @param idUsuario usuario autor
     * @param ip IP de origen
     * @param documento documento relacionado
     * @param anterior saldo antes
     * @param nuevo saldo después
     * @param motivo motivo (opcional)
     * @return movimiento persistido
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoInventario registrar(TipoMovimiento tipo, Long idProducto, BigDecimal cantidad, Integer idUsuario,
                                          String ip, String documento, BigDecimal anterior, BigDecimal nuevo,
                                          String motivo) {
        return movimientos.save(MovimientoInventario.builder()
                .tipo(tipo).idProducto(idProducto).cantidad(cantidad)
                .fechaHora(LocalDateTime.ofInstant(reloj.instant(), java.time.ZoneOffset.UTC))
                .idUsuario(idUsuario).ip(ip).documentoRelacionado(documento)
                .valorAnterior(anterior).valorNuevo(nuevo).motivo(motivo).build());
    }

    /**
     * Consulta paginada, más recientes primero.
     *
     * @param idProducto filtro opcional
     * @param pagina página (base 0)
     * @param tamano tamaño, máximo 100
     * @return página de movimientos
     */
    @Transactional(readOnly = true)
    public PaginaResponse<MovimientoResponse> consultar(Long idProducto, int pagina, int tamano) {
        Pageable p = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaHora", "id"));
        var page = idProducto == null ? movimientos.findAll(p) : movimientos.findByIdProducto(idProducto, p);
        return PaginaResponse.de(page, MovimientoResponse::de);
    }
}
