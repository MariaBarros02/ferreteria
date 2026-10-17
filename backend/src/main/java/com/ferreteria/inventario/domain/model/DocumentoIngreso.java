/*
 * Nombre: DocumentoIngreso.java
 * Descripción: Entidad JPA del documento de ingreso generado por cada recepción (RF-17).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import java.time.LocalDateTime;

/**
 * Documento de ingreso con consecutivo propio, respaldado por la factura del proveedor. Solo activo o anulado (RN-09).
 */
@Entity
@Table(name = "documento_ingreso")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentoIngreso {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento_ingreso")
    private Long id;

    /** Consecutivo (ING-001000). */
    @Column(name = "consecutivo", nullable = false, unique = true, length = 30)
    private String consecutivo;

    /** Estado. */
    @Convert(converter = Convertidores.EstadoDocumentoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoDocumento estado;

    /** Número de la factura del proveedor que respalda el ingreso. */
    @Column(name = "factura_proveedor", nullable = false, length = 100)
    private String facturaProveedor;

    /** Motivo de anulación. */
    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    /** Fecha de la recepción. */
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Compra recibida. */
    @Column(name = "id_compra")
    private Long idCompra;

    /** Usuario que recibió. */
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Observaciones. */
    @Column(name = "observaciones", length = 255)
    private String observaciones;

    /** Clave de idempotencia: evita contabilizar dos veces la misma recepción. */
    @Column(name = "id_operacion", length = 64)
    private String idOperacion;

    /** Fecha de anulación. */
    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    /** Usuario que anuló. */
    @Column(name = "id_usuario_anulacion")
    private Integer idUsuarioAnulacion;
}
