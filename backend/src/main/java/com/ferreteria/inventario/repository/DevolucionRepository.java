/*
 * Nombre: DevolucionRepository.java
 * Descripción: Repositorio JPA de líneas de devolución y acumulados devueltos.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.model.Devolucion;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Devolucion}.
 */
public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {
    /**
     * Líneas de un documento de devolución.
     *
     * @param idDocumento documento
     * @return líneas
     */
    List<Devolucion> findByIdDocumentoDevolucionOrderByIdAsc(Long idDocumento);

    /**
     * Cantidad ya devuelta de una línea de venta en documentos activos.
     *
     * @param idLineaVenta línea de venta
     * @param activo estado activo
     * @return cantidad acumulada
     */
    @Query("""
           select coalesce(sum(d.cantidad), 0) from Devolucion d, DocumentoDevolucion dd
           where dd.id = d.idDocumentoDevolucion and dd.estado = :activo and d.idLineaVenta = :linea
           """)
    BigDecimal devueltoDeLineaVenta(@Param("linea") Long idLineaVenta, @Param("activo") EstadoDocumento activo);

    /**
     * Cantidad ya devuelta al proveedor de una línea de compra en documentos activos.
     *
     * @param idLineaCompra línea de compra
     * @param activo estado activo
     * @return cantidad acumulada
     */
    @Query("""
           select coalesce(sum(d.cantidad), 0) from Devolucion d, DocumentoDevolucion dd
           where dd.id = d.idDocumentoDevolucion and dd.estado = :activo and d.idLineaCompra = :linea
           """)
    BigDecimal devueltoDeLineaCompra(@Param("linea") Long idLineaCompra, @Param("activo") EstadoDocumento activo);

    /**
     * Cantidad ya devuelta de una línea de venta a un lote concreto, en documentos activos.
     *
     * @param idLineaVenta línea de venta
     * @param idLote lote
     * @param activo estado activo
     * @return cantidad acumulada
     */
    @Query("""
           select coalesce(sum(d.cantidad), 0) from Devolucion d, DocumentoDevolucion dd
           where dd.id = d.idDocumentoDevolucion and dd.estado = :activo and d.idLineaVenta = :linea and d.idLote = :lote
           """)
    BigDecimal devueltoDeLineaVentaYLote(@Param("linea") Long idLineaVenta, @Param("lote") Long idLote,
                                         @Param("activo") EstadoDocumento activo);

}
