/*
 * Nombre: MovimientoInventario.java
 * Descripción: Entidad JPA de movimiento de inventario / auditoría con los ocho campos de RF-08; inmutable (RN-16).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.domain.model;

import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Movimiento auditable. Un movimiento histórico nunca se modifica: un disparador en la
 * base de datos lo impide; las correcciones se registran con {@code idMovimientoOriginal}.
 */
@Entity
@Table(name = "movimiento_inventario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MovimientoInventario {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long id;

    /** Tipo de movimiento. */
    @Convert(converter = Convertidores.TipoMovimientoConverter.class)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    /** Producto afectado. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Cantidad con signo: positivo entra, negativo sale. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;

    /** Fecha y hora del movimiento. */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    /** Usuario autor. */
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** Dirección IP de origen. */
    @Column(name = "ip", columnDefinition = "inet")
    private String ip;

    /** Documento relacionado (venta, compra, ajuste…). */
    @Column(name = "documento_relacionado", nullable = false, length = 100)
    private String documentoRelacionado;

    /** Saldo antes del movimiento. */
    @Column(name = "valor_anterior", nullable = false, precision = 14, scale = 4)
    private BigDecimal valorAnterior;

    /** Saldo después del movimiento. */
    @Column(name = "valor_nuevo", nullable = false, precision = 14, scale = 4)
    private BigDecimal valorNuevo;

    /** Motivo (obligatorio en ajustes y anulaciones). */
    @Column(length = 255)
    private String motivo;

    /** Movimiento original que este rectifica (RN-16). */
    @Column(name = "id_movimiento_original")
    private Long idMovimientoOriginal;

    /** Saldo afectado (disponible, reservado o dañado); valor anterior/nuevo se refieren a este saldo. */
    @Builder.Default
    @Convert(converter = Convertidores.EstadoInventarioConverter.class)
    @Column(name = "estado_inventario", nullable = false)
    private EstadoInventario estadoInventario = EstadoInventario.DISPONIBLE;

    /** Lote afectado, si aplica. */
    @Column(name = "id_lote")
    private Long idLote;
}
