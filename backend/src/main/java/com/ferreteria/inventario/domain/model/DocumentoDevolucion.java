/*
 * Nombre: DocumentoDevolucion.java
 * Descripción: Entidad JPA del documento de devolución (RF-13): operación independiente vinculada al documento original.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.TipoDevolucion;
import com.ferreteria.inventario.domain.enums.TratamientoDevolucion;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Devolución de cliente (vinculada a una venta) o a proveedor (vinculada a una compra). La factura original no se modifica.
 */
@Entity
@Table(name = "documento_devolucion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentoDevolucion {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento_devolucion")
    private Long id;

    /** Consecutivo (DEV-000001). */
    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    /** Cliente o proveedor. */
    @Convert(converter = Convertidores.TipoDevolucionConverter.class)
    @Column(name = "tipo", nullable = false)
    private TipoDevolucion tipo;

    /** Venta original (devolución de cliente). */
    @Column(name = "id_venta")
    private Long idVenta;

    /** Compra original (devolución a proveedor). */
    @Column(name = "id_compra")
    private Long idCompra;

    /** Fecha. */
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Usuario responsable. */
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** Motivo. */
    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    /** Tratamiento del importe. */
    @Convert(converter = Convertidores.TratamientoDevolucionConverter.class)
    @Column(name = "tratamiento", nullable = false)
    private TratamientoDevolucion tratamiento;

    /** Importe total de la devolución. */
    @Builder.Default
    @Column(name = "total", nullable = false, precision = 16, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Estado. */
    @Convert(converter = Convertidores.EstadoDocumentoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoDocumento estado;

    /** Observaciones. */
    @Column(name = "observaciones", length = 255)
    private String observaciones;

    /** Clave de idempotencia. */
    @Column(name = "id_operacion", length = 64)
    private String idOperacion;
}
