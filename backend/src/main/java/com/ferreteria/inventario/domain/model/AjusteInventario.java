/*
 * Nombre: AjusteInventario.java
 * Descripción: Entidad JPA de ajuste de inventario con flujo de aprobación (RF-14).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.TipoAjuste;
import com.ferreteria.inventario.domain.enums.EstadoAprobacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ajuste de inventario: queda pendiente hasta que un usuario autorizado lo aprueba; solo entonces cambia el saldo.
 */
@Entity
@Table(name = "ajuste_inventario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AjusteInventario {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ajuste")
    private Long id;

    /** Consecutivo (AJ-000001). */
    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    /** Producto. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Lote (opcional). */
    @Column(name = "id_lote")
    private Long idLote;

    /** Entrada o salida. */
    @Convert(converter = Convertidores.TipoAjusteConverter.class)
    @Column(name = "tipo", nullable = false)
    private TipoAjuste tipo;

    /** Cantidad en unidad base (positiva). */
    @Column(name = "cantidad", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;

    /** Motivo. */
    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    /** Observaciones. */
    @Column(name = "observaciones", length = 500)
    private String observaciones;

    /** Estado de aprobación. */
    @Convert(converter = Convertidores.EstadoAprobacionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoAprobacion estado;

    /** Usuario que solicita. */
    @Column(name = "id_usuario_solicita", nullable = false)
    private Integer idUsuarioSolicita;

    /** Fecha de solicitud. */
    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    /** Usuario que aprueba o rechaza. */
    @Column(name = "id_usuario_decide")
    private Integer idUsuarioDecide;

    /** Fecha de la decisión. */
    @Column(name = "fecha_decision")
    private LocalDateTime fechaDecision;

    /** Motivo del rechazo. */
    @Column(name = "motivo_rechazo", length = 255)
    private String motivoRechazo;

    /** Saldo disponible al solicitar. */
    @Column(name = "saldo_sistema", precision = 14, scale = 4)
    private BigDecimal saldoSistema;

    /** Movimiento generado al aprobar. */
    @Column(name = "id_movimiento")
    private Long idMovimiento;

    /** Movimiento que este ajuste rectifica (nota de ajuste, RN-16). */
    @Column(name = "id_movimiento_original")
    private Long idMovimientoOriginal;

    /** Conteo que originó el ajuste. */
    @Column(name = "id_conteo")
    private Long idConteo;

    /** Clave de idempotencia. */
    @Column(name = "id_operacion", length = 64)
    private String idOperacion;
}
