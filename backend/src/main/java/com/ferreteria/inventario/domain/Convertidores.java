/*
 * Nombre: Convertidores.java
 * Descripción: Convertidores JPA entre enumerados Java y los tipos ENUM nativos de PostgreSQL.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
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
    /** Convertidor de {@link EstadoInventario}. */
    @Converter public static class EstadoInventarioConverter extends Base<EstadoInventario> {
        public EstadoInventarioConverter() { super(EstadoInventario.class); }
    }
    /** Convertidor de {@link EstadoReserva}. */
    @Converter public static class EstadoReservaConverter extends Base<EstadoReserva> {
        public EstadoReservaConverter() { super(EstadoReserva.class); }
    }
    /** Convertidor de {@link EstadoAprobacion}. */
    @Converter public static class EstadoAprobacionConverter extends Base<EstadoAprobacion> {
        public EstadoAprobacionConverter() { super(EstadoAprobacion.class); }
    }
    /** Convertidor de {@link EstadoSesionConteo}. */
    @Converter public static class EstadoSesionConteoConverter extends Base<EstadoSesionConteo> {
        public EstadoSesionConteoConverter() { super(EstadoSesionConteo.class); }
    }
    /** Convertidor de {@link EstadoConteo}. */
    @Converter public static class EstadoConteoConverter extends Base<EstadoConteo> {
        public EstadoConteoConverter() { super(EstadoConteo.class); }
    }
    /** Convertidor de {@link TipoDevolucion}. */
    @Converter public static class TipoDevolucionConverter extends Base<TipoDevolucion> {
        public TipoDevolucionConverter() { super(TipoDevolucion.class); }
    }
    /** Convertidor de {@link EstadoProductoDevuelto}. */
    @Converter public static class EstadoProductoDevueltoConverter extends Base<EstadoProductoDevuelto> {
        public EstadoProductoDevueltoConverter() { super(EstadoProductoDevuelto.class); }
    }
    /** Convertidor de {@link TratamientoDevolucion}. */
    @Converter public static class TratamientoDevolucionConverter extends Base<TratamientoDevolucion> {
        public TratamientoDevolucionConverter() { super(TratamientoDevolucion.class); }
    }
    /** Convertidor de {@link TipoAjuste}. */
    @Converter public static class TipoAjusteConverter extends Base<TipoAjuste> {
        public TipoAjusteConverter() { super(TipoAjuste.class); }
    }
    /** Convertidor de {@link EstadoCierre}. */
    @Converter public static class EstadoCierreConverter extends Base<EstadoCierre> {
        public EstadoCierreConverter() { super(EstadoCierre.class); }
    }
    /** Convertidor de {@link EstadoImportacion}. */
    @Converter public static class EstadoImportacionConverter extends Base<EstadoImportacion> {
        public EstadoImportacionConverter() { super(EstadoImportacion.class); }
    }
}
