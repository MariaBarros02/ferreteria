/*
 * Nombre: PaginaResponse.java
 * Descripción: Envoltorio JSON estable para respuestas paginadas.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Página de resultados.
 *
 * @param contenido elementos de la página
 * @param pagina número de página (base 0)
 * @param tamano tamaño de página
 * @param totalElementos total de elementos
 * @param totalPaginas total de páginas
 * @param <T> tipo de elemento
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    /**
     * Convierte una {@link Page} aplicando un mapeo a DTO.
     *
     * @param page página de entidades
     * @param mapeo función entidad → DTO
     * @param <E> tipo de entidad
     * @param <T> tipo de DTO
     * @return página de DTOs
     */
    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapeo) {
        return new PaginaResponse<>(page.getContent().stream().map(mapeo).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
