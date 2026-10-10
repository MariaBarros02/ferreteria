/*
 * Nombre: ProductoServiceTest.java
 * Descripción: Pruebas unitarias del servicio de productos: unicidad RN-01, saldo inicial en cero, eliminación y unidades.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.ConversionRequest;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.ProductoResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ConversionUnidadRepository;
import com.ferreteria.inventario.repository.InventarioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.service.ProductoService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Casos UT-PRO-01 … UT-PRO-07. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoServiceTest {

    @Mock ProductoRepository productos;
    @Mock InventarioRepository inventarios;
    @Mock ConversionUnidadRepository conversiones;
    ProductoService servicio;

    ProductoRequest req = new ProductoRequest(" Taladro ", "Bosch", "500W", "7701234500015", "unidad",
            TipoRastreo.SERIE, false, true);

    @BeforeEach
    void preparar() {
        servicio = new ProductoService(productos, inventarios, conversiones);
        when(productos.save(any(Producto.class))).thenAnswer(a -> { Producto p = a.getArgument(0); p.setId(10L); return p; });
        when(productos.findByCodigoBarras(any())).thenReturn(Optional.empty());
        when(productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test @DisplayName("UT-PRO-01 crear normaliza espacios y crea saldo inicial en cero")
    void crear() {
        ProductoResponse r = servicio.crear(req);
        assertThat(r.nombre()).isEqualTo("Taladro");
        ArgumentCaptor<Inventario> cap = ArgumentCaptor.forClass(Inventario.class);
        verify(inventarios).save(cap.capture());
        assertThat(cap.getValue().getDisponible()).isEqualByComparingTo("0");
        assertThat(cap.getValue().getIdProducto()).isEqualTo(10L);
    }

    @Test @DisplayName("UT-PRO-02 código de barras duplicado se rechaza (RN-01)")
    void codigoDuplicado() {
        when(productos.findByCodigoBarras("7701234500015")).thenReturn(Optional.of(Producto.builder().id(99L).build()));
        assertThatThrownBy(() -> servicio.crear(req))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("CODIGO_DUPLICADO"));
        verify(productos, never()).save(any());
    }

    @Test @DisplayName("UT-PRO-03 misma combinación producto+marca+presentación se rechaza (RN-01)")
    void referenciaDuplicada() {
        when(productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase("Taladro", "Bosch", "500W"))
                .thenReturn(Optional.of(Producto.builder().id(99L).build()));
        assertThatThrownBy(() -> servicio.crear(req))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("REFERENCIA_DUPLICADA"));
    }

    @Test @DisplayName("UT-PRO-04 actualizar el mismo producto con su propio código es válido")
    void actualizarMismoCodigo() {
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).build()));
        when(productos.findByCodigoBarras("7701234500015")).thenReturn(Optional.of(Producto.builder().id(10L).build()));
        assertThat(servicio.actualizar(10L, req).id()).isEqualTo(10L);
    }

    @Test @DisplayName("UT-PRO-05 consultar un producto inexistente lanza 404")
    void noEncontrado() {
        when(productos.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.obtener(5L))
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test @DisplayName("UT-PRO-06 no se elimina un producto con existencias")
    void eliminarConExistencias() {
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).build()));
        when(inventarios.findById(10L)).thenReturn(Optional.of(Inventario.builder().idProducto(10L)
                .disponible(new BigDecimal("3")).reservado(BigDecimal.ZERO).danado(BigDecimal.ZERO).build()));
        assertThatThrownBy(() -> servicio.eliminar(10L))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PRODUCTO_CON_EXISTENCIAS"));
        verify(productos, never()).deleteById(any());
    }

    @Test @DisplayName("UT-PRO-07 conversiones repetidas o iguales a la unidad base se rechazan")
    void conversionesInvalidas() {
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).unidadBase("unidad").nombre("T").build()));
        var caja = new ConversionRequest("caja", new BigDecimal("100"), 0);
        assertThatThrownBy(() -> servicio.configurarUnidades(10L, List.of(caja, caja))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> servicio.configurarUnidades(10L, List.of(new ConversionRequest("unidad", BigDecimal.ONE, 0))))
                .isInstanceOf(ApiException.class);
    }
}
