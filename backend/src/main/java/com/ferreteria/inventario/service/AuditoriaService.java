/*
 * Nombre: AuditoriaService.java
 * Descripción: Servicio de auditoría: registra movimientos inmutables con los ocho campos de RF-08 y permite consultarlos con filtros.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import com.ferreteria.inventario.dto.MovimientoResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.repository.MovimientoInventarioRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
     * Filtros de consulta de movimientos.
     *
     * @param idProducto producto
     * @param tipo tipo de movimiento
     * @param idUsuario usuario autor
     * @param documento texto contenido en el documento relacionado
     * @param desde fecha inicial (inclusive)
     * @param hasta fecha final (inclusive)
     */
    public record Filtros(Long idProducto, TipoMovimiento tipo, Integer idUsuario, String documento,
                          LocalDate desde, LocalDate hasta) { }

    /**
     * Registra un movimiento sobre el saldo disponible dentro de la transacción en curso (todo o nada, RNF-02).
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
        return registrar(tipo, idProducto, cantidad, idUsuario, ip, documento, anterior, nuevo, motivo,
                EstadoInventario.DISPONIBLE, null, null);
    }

    /**
     * Registra un movimiento indicando el saldo afectado, el lote y el movimiento que rectifica (RN-16).
     *
     * @param tipo tipo de movimiento
     * @param idProducto producto afectado
     * @param cantidad cantidad con signo
     * @param idUsuario usuario autor
     * @param ip IP de origen
     * @param documento documento relacionado
     * @param anterior saldo antes (del estado indicado)
     * @param nuevo saldo después (del estado indicado)
     * @param motivo motivo (opcional)
     * @param estado saldo afectado
     * @param idLote lote (opcional)
     * @param idMovimientoOriginal movimiento rectificado (opcional)
     * @return movimiento persistido
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoInventario registrar(TipoMovimiento tipo, Long idProducto, BigDecimal cantidad, Integer idUsuario,
                                          String ip, String documento, BigDecimal anterior, BigDecimal nuevo,
                                          String motivo, EstadoInventario estado, Long idLote,
                                          Long idMovimientoOriginal) {
        return movimientos.save(MovimientoInventario.builder()
                .tipo(tipo).idProducto(idProducto).cantidad(cantidad)
                .fechaHora(LocalDateTime.ofInstant(reloj.instant(), java.time.ZoneOffset.UTC))
                .idUsuario(idUsuario).ip(ip).documentoRelacionado(documento)
                .valorAnterior(anterior).valorNuevo(nuevo).motivo(motivo)
                .estadoInventario(estado == null ? EstadoInventario.DISPONIBLE : estado)
                .idLote(idLote).idMovimientoOriginal(idMovimientoOriginal).build());
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

    /**
     * Consulta paginada con filtros combinables, más recientes primero.
     *
     * @param f filtros
     * @param zona zona horaria de las fechas del filtro
     * @param pagina página (base 0)
     * @param tamano tamaño, máximo 100
     * @return página de movimientos
     */
    @Transactional(readOnly = true)
    public PaginaResponse<MovimientoResponse> consultarFiltrado(Filtros f, java.time.ZoneId zona, int pagina, int tamano) {
        Pageable p = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaHora", "id"));
        return PaginaResponse.de(movimientos.findAll(especificacion(f, zona), p), MovimientoResponse::de);
    }

    static Specification<MovimientoInventario> especificacion(Filtros f, java.time.ZoneId zona) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.idProducto() != null) {
                ps.add(cb.equal(root.get("idProducto"), f.idProducto()));
            }
            if (f.tipo() != null) {
                ps.add(cb.equal(root.get("tipo"), f.tipo()));
            }
            if (f.idUsuario() != null) {
                ps.add(cb.equal(root.get("idUsuario"), f.idUsuario()));
            }
            if (f.documento() != null && !f.documento().isBlank()) {
                String t = "%" + f.documento().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                ps.add(cb.like(cb.lower(root.<String>get("documentoRelacionado")), t));
            }
            if (f.desde() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("fechaHora"), Fechas.inicioUtc(f.desde(), zona)));
            }
            if (f.hasta() != null) {
                ps.add(cb.lessThan(root.<LocalDateTime>get("fechaHora"), Fechas.finExclusivoUtc(f.hasta(), zona)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
