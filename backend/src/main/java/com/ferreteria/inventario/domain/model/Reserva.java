/*
 * Nombre: Reserva.java
 * Descripción: Entidad JPA de reserva de inventario para un cliente (RF-05, RN-03).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reserva: pasa unidades de disponible a reservado hasta su venta, cancelación o vencimiento.
 */
@Entity
@Table(name = "reserva")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reserva {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Long id;

    /** Producto. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Cliente. */
    @Column(name = "id_cliente", nullable = false)
    private Long idCliente;

    /** Pedido o referencia del cliente. */
    @Column(name = "id_pedido", nullable = false, length = 50)
    private String idPedido;

    /** Cantidad reservada en unidad base. */
    @Column(name = "cantidad", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;

    /** Fecha límite de la reserva. */
    @Column(name = "fecha_limite", nullable = false)
    private LocalDate fechaLimite;

    /** Estado. */
    @Convert(converter = Convertidores.EstadoReservaConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoReserva estado;

    /** Usuario que reservó. */
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Fecha de creación. */
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    /** Fecha de liberación. */
    @Column(name = "fecha_liberacion")
    private LocalDateTime fechaLiberacion;

    /** Venta que consumió la reserva. */
    @Column(name = "id_venta")
    private Long idVenta;

    /** Motivo de la liberación. */
    @Column(name = "motivo_liberacion", length = 255)
    private String motivoLiberacion;
}
