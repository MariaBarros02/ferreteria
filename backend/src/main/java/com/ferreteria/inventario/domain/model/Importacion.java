/*
 * Nombre: Importacion.java
 * Descripción: Entidad JPA de importación de Excel validada, revisada e importada (RF-10, RN-15).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoImportacion;
import java.time.LocalDateTime;

/**
 * Importación: guarda el resultado de la validación por fila hasta que el usuario la confirma o cancela.
 */
@Entity
@Table(name = "importacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Importacion {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_importacion")
    private Long id;

    /** Tipo de datos (productos o terceros). */
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    /** Nombre del archivo cargado. */
    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    /** SHA-256 del archivo. */
    @Column(name = "hash_archivo", nullable = false, length = 64)
    private String hashArchivo;

    /** Estado. */
    @Convert(converter = Convertidores.EstadoImportacionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoImportacion estado;

    /** Filas de datos leídas. */
    @Column(name = "total_filas", nullable = false)
    private int totalFilas;

    /** Filas válidas. */
    @Column(name = "filas_validas", nullable = false)
    private int filasValidas;

    /** Filas con errores. */
    @Column(name = "filas_error", nullable = false)
    private int filasError;

    /** Filas marcadas como posible duplicado. */
    @Column(name = "filas_duplicadas", nullable = false)
    private int filasDuplicadas;

    /** Filas efectivamente importadas. */
    @Column(name = "filas_importadas")
    private Integer filasImportadas;

    /** Filas validadas en JSON. */
    @Column(name = "filas", nullable = false, columnDefinition = "jsonb")
    private String filas;

    /** Usuario que cargó el archivo. */
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** Fecha de carga. */
    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    /** Fecha de confirmación o cancelación. */
    @Column(name = "fecha_proceso")
    private LocalDateTime fechaProceso;
}
