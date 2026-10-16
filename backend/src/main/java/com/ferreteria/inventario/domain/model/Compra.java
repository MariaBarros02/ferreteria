/*
 * Nombre: Compra.java
 * Descripción: Entidad JPA de compra (pedido a proveedor) con sus líneas (RF-03).
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
import java.util.ArrayList;
import java.util.List;

/**
 * Compra registrada a un proveedor: representa lo pedido. El inventario solo aumenta con la recepción (RF-17).
 */
@Entity
@Table(name = "compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Compra {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_compra")
    private Long id;

    /** Consecutivo de la compra (OC-000001). */
    @Column(name = "numero", length = 30)
    private String numero;

    /** Proveedor. */
    @Column(name = "id_proveedor", nullable = false)
    private Long idProveedor;

    /** Fecha de registro. */
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Estado del documento (activo o anulado, RN-09). */
    @Convert(converter = Convertidores.EstadoDocumentoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoDocumento estado;

    /** Motivo de anulación. */
    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    /** Fecha de anulación. */
    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    /** Usuario que registró la compra. */
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Observaciones. */
    @Column(name = "observaciones", length = 255)
    private String observaciones;

    /** Clave de idempotencia. */
    @Column(name = "id_operacion", length = 64)
    private String idOperacion;

    /** Líneas de la compra. */
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL)
    @OrderBy("id")
    @Builder.Default
    private List<LineaCompra> lineas = new ArrayList<>();
}
