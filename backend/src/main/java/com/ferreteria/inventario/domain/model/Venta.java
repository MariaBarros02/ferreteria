/*
 * Nombre: Venta.java
 * Descripción: Entidad JPA de venta con sus líneas; el número interno es distinto de la numeración fiscal DIAN (RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * Venta confirmada. Los documentos nunca se eliminan: solo se anulan (RN-09).
 */
@Entity
@Table(name = "venta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Venta {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Long id;

    /** Número interno único (p. ej. VTA-001000). */
    @Column(name = "numero_interno", nullable = false, unique = true, length = 30)
    private String numeroInterno;

    /** Identificador del cliente. */
    @Column(name = "id_cliente", nullable = false)
    private Long idCliente;

    /** Fecha y hora de confirmación. */
    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Estado del documento. */
    @Convert(converter = Convertidores.EstadoDocumentoConverter.class)
    @Column(nullable = false)
    private EstadoDocumento estado;

    /** Líneas de la venta. */
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<LineaVenta> lineas = new ArrayList<>();
}
