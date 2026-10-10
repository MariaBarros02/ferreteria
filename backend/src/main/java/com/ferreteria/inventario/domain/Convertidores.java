/*
 * Nombre: Convertidores.java
 * Descripción: Convertidores JPA entre enumerados Java y los tipos ENUM nativos de PostgreSQL.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain;

import com.ferreteria.inventario.domain.enums.*;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Contenedor de convertidores JPA. Cada convertidor traduce el nombre Java
 * (p. ej. {@code NATURAL}) al literal de la base de datos ({@code natural}).
 */
public final class Convertidores {

    private Convertidores() { }

    /**
     * Convertidor genérico para enumerados que implementan {@link ValorPersistente}.
     *
     * @param <E> tipo de enumerado
     */
    public abstract static class Base<E extends Enum<E> & ValorPersistente>
            implements AttributeConverter<E, String> {

        private final Class<E> tipo;

        protected Base(Class<E> tipo) {
            this.tipo = tipo;
        }

        @Override
        public String convertToDatabaseColumn(E atributo) {
            return atributo == null ? null : atributo.getValor();
        }

        @Override
        public E convertToEntityAttribute(String columna) {
            if (columna == null) {
                return null;
            }
            for (E e : tipo.getEnumConstants()) {
                if (e.getValor().equals(columna)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Valor desconocido para " + tipo.getSimpleName() + ": " + columna);
        }
    }

    /** Convertidor de {@link TipoPersona}. */
    @Converter public static class TipoPersonaConverter extends Base<TipoPersona> {
        public TipoPersonaConverter() { super(TipoPersona.class); }
    }
    /** Convertidor de {@link TipoIdentificacion}. */
    @Converter public static class TipoIdentificacionConverter extends Base<TipoIdentificacion> {
        public TipoIdentificacionConverter() { super(TipoIdentificacion.class); }
    }
    /** Convertidor de {@link TipoRastreo}. */
    @Converter public static class TipoRastreoConverter extends Base<TipoRastreo> {
        public TipoRastreoConverter() { super(TipoRastreo.class); }
    }
    /** Convertidor de {@link EstadoDocumento}. */
    @Converter public static class EstadoDocumentoConverter extends Base<EstadoDocumento> {
        public EstadoDocumentoConverter() { super(EstadoDocumento.class); }
    }
    /** Convertidor de {@link EstadoCuenta}. */
    @Converter public static class EstadoCuentaConverter extends Base<EstadoCuenta> {
        public EstadoCuentaConverter() { super(EstadoCuenta.class); }
    }
    /** Convertidor de {@link ModalidadFactura}. */
    @Converter public static class ModalidadFacturaConverter extends Base<ModalidadFactura> {
        public ModalidadFacturaConverter() { super(ModalidadFactura.class); }
    }
    /** Convertidor de {@link TipoMovimiento}. */
    @Converter public static class TipoMovimientoConverter extends Base<TipoMovimiento> {
        public TipoMovimientoConverter() { super(TipoMovimiento.class); }
    }
}
