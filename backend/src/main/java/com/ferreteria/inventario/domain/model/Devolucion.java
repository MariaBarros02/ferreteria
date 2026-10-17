/*
 * Nombre: Devolucion.java
 * Descripción: Entidad JPA de línea de devolución con su movimiento de inventario (RF-13).
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
import com.ferreteria.inventario.domain.enums.EstadoProductoDevuelto;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import java.math.BigDecimal;

/**
 * Línea de devolución: producto, cantidad, estado del producto y movimiento generado.
 */
@Entity
@Table(name = "devolucion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Devolucion {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_devolucion")
    private Long id;

    /** Cliente o proveedor. */
    @Convert(converter = Convertidores.TipoDevolucionConverter.class)
    @Column(name = "tipo", nullable = false)
    private TipoDevolucion tipo;

    /** Número del documento original (compatibilidad con la versión 1.0). */
    @Column(name = "id_documento_origen", nullable = false, length = 50)
    private String idDocumentoOrigen;

    /** Producto. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Cantidad devuelta en unidad base. */
    @Column(name = "cantidad", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;

    /** Motivo. */
    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    /** Movimiento de inventario. */
    @Column(name = "id_movimiento", nullable = false)
    private Long idMovimiento;

    /** Documento de devolución. */
    @Column(name = "id_documento_devolucion")
    private Long idDocumentoDevolucion;

    /** Línea de venta devuelta. */
    @Column(name = "id_linea_venta")
    private Long idLineaVenta;

    /** Línea de compra devuelta. */
    @Column(name = "id_linea_compra")
    private Long idLineaCompra;

    /** Lote afectado. */
    @Column(name = "id_lote")
    private Long idLote;

    /** Estado del producto devuelto por el cliente. */
    @Convert(converter = Convertidores.EstadoProductoDevueltoConverter.class)
    @Column(name = "estado_producto")
    private EstadoProductoDevuelto estadoProducto;

    /** Saldo de origen en devoluciones a proveedor. */
    @Convert(converter = Convertidores.EstadoInventarioConverter.class)
    @Column(name = "origen_estado")
    private EstadoInventario origenEstado;

    /** Importe de la línea. */
    @Column(name = "valor", precision = 16, scale = 2)
    private BigDecimal valor;
}
