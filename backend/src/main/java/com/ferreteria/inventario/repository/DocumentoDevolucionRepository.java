/*
 * Nombre: DocumentoDevolucionRepository.java
 * Descripción: Repositorio JPA de documentos de devolución.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.DocumentoDevolucion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Acceso a datos de {@link DocumentoDevolucion}.
 */
public interface DocumentoDevolucionRepository extends JpaRepository<DocumentoDevolucion, Long>, JpaSpecificationExecutor<DocumentoDevolucion> {
    /**
     * Siguiente valor de la secuencia seq_devolucion.
     *
     * @return consecutivo
     */
    @Query(value = "select nextval('seq_devolucion')", nativeQuery = true)
    long siguienteNumero();

    /**
     * Documento registrado con una clave de idempotencia.
     *
     * @param idOperacion clave
     * @return documento, si existe
     */
    Optional<DocumentoDevolucion> findByIdOperacion(String idOperacion);

    /**
     * Devoluciones de una venta.
     *
     * @param idVenta venta
     * @return documentos
     */
    List<DocumentoDevolucion> findByIdVentaOrderByIdAsc(Long idVenta);

    /**
     * Devoluciones de una compra.
     *
     * @param idCompra compra
     * @return documentos
     */
    List<DocumentoDevolucion> findByIdCompraOrderByIdAsc(Long idCompra);

}
